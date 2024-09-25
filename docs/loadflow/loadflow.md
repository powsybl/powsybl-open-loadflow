# Modeling and equations

## Grid modeling

Open Load Flow computes power flows from IIDM grid model in bus/view topology. From the view, a very simple network, composed
of only buses and branches is created. In the graph vision, we rely on a $$\Pi$$ model for branches (lines, transformers, dangling lines, etc.):

- $R$ and $X$ are respectively the real part (resistance) and the imaginary part (reactance) of the complex impedance ;  
- $G_1$ and $G_2$ are the real parts (conductance) on respectively side 1 and side 2 of the branch ;
- $B_1$ and $B_2$ are the imaginary parts (susceptance) on respectively side 1 and side 2 of the branch ;
- $A_1$ is the angle shifting on side 1, before the series impedance. For classical branches, the default value is zero ;
- $\rho_1$ is the ratio of voltages between side 2 and side 1, before the series impedance. For classical branches, the default value is $1$.

As the $\Pi$ model is created from IIDM grid modeling that locates its ratio and phase tap changers in side 1, $A_2$ and $\rho_2$ are always
equal to zero and $1$. In case of a branch with voltage or phase control, the $\Pi$ model becomes an array. See below our model:

![Pi model](pi-model.svg){class="only-light"}
![Pi model](pi-model-dark-mode.svg){class="only-dark"}

### HVDC line

Open Load Flow also supports networks with HVDC lines (High Voltage Direct Current lines). An HVDC line is connected to the rest of the AC network through HVDC converter stations, that can be either LCC (Line-Commutated Converter) or VSC (Voltage-Source Converter).

(ac-flow-computing)=
## AC flows computing

AC flows computing in OpenLoadFLow relies on solving a system of non-linear squared equations, where the unknowns are voltage magnitude and phase angle at each bus of the network, implying that there are $2N$ unknowns where $N$ is the number of buses. There are two equations per network bus, resulting in $2N$ equations. The nature of these $2$ equations depends on the type of the bus:
- PQ-bus: active and reactive balance are fixed at the bus,
- PV-bus: active balance and voltage magnitude are fixed at the bus.

Moreover, at the slack bus, the active balance equation is removed and replaced by an equation fixing the voltage phase angle at 0.

Let $v_i$ be the unknown voltage magnitude at bus $i$. Let $\theta_i$ be the unknown voltage phase angle at bus $i$. Equation fixing voltage magnitude to a reference (also called target) is simply written $v_i = V^{ref}_i$. Equation fixing voltage phase angle at slack bus $i$ is: $\phi_i = 0$

To build the active and reactive balance equations, Open Load Flow first expresses active and reactive power flowing from a bus to another through a line:

$$p_{i,j}= \rho_iv_i(G_i\rho_iv_i + Y\rho_iv_i\text{sin}(\Xi) - Y\rho_jv_j\text{sin}(\theta))$$

$$q_{i,j}= \rho_iv_i(-B_i\rho_iv_i + Y\rho_iv_i\text{cos}(\Xi) - Y\rho_jv_j\text{cos}(\theta))$$

Where $Y$ is the magnitude of the line complex admittance $\frac{1}{R+jX}$, and $\Xi$ such that: $R+jX = \frac{1}{Y}e^{j(\frac{\pi}{2}-\Xi)}$. $\theta$ satisfies: $\theta= \Xi - A_i + A_j - \phi_i + \phi_j.$

Beware that $p_{i,j}$ is the power that goes out from the bus $i$.

Therefore, active and reactive balance equations are expressed as:

$$ P_i^{in} = \sum_{j \in \delta(i)} p_{i,j}$$

$$ Q_i^{in} = \sum_{j \in \delta(i)} q_{i,j}$$

where $\delta(i)$ is the set of buses linked to $i$ in the network graph.

The resulting non-linear system of equations is solved via the Newton-Raphson algorithm.
The underlying principle of the algorithm is the following:
- It starts at a certain point $x_0 = (v_0, \phi_0)$ as an approximate solution to the system of equations;
- Then, in an iterative fashion, it generates a series $x_1, x_2,.., x_k$ of better approximate solutions to the system of equations;
- These iterates $x_k$ are found by solving a system of equations using local Jacobian matrix $J(v,\phi)$ at the previous point $x_{k-1}$; 

See **acSolverType** below for more details.

### Other regulation modes

PQ-bus and PV-bus are used to model local voltage magnitude or local reactive power controls. Other controls are supported in OpenLoadFLow:
- Remote voltage control for generators, static var compensators and two and three windings transformers with ratio tap changer. Control shared over several controllers buses is supported ;
- Remote reactive power control for generators ;
- For static var compensator with a voltage set point, the support of a voltage per reactive power control, also called slope, that modifies a bit the local voltage at connection bus. We only support a local control. 

#### Remote voltage control

In our explanation, we have two buses. A generator or more is connected to bus $b_1$, that is called controller bus. The remote bus $b_2$, where voltage should reach the target, is called controlled bus. The bus $b_1$ is no longer a PQ-bus and becomes a P-bus: only active power balance is fixed for that bus. Bus $b_2$ becomes a PQV-bus, where the voltage magnitude is fixed at the value defined by the voltage control. To resume:
- At controller bus $b_1$:
    - ${b_1}^{in} = \sum_{j \in v(b_1)} p_{b_1,j}$.
- At controlled bus $b_2$:
    - $P_{b_2}^{in} = \sum_{j \in v(b_2)} p_{b_2,j}$.
    - $Q_{b_2}^{in} = \sum_{j \in v(b_2)} q_{b_2,j}$.
    - $v_{b_2} = V^{c}_{b_1}$.
    
#### Remote reactive power control

A bus $b_1$ has, through a generator, a remote reactive power control on a branch $(i,j)$. This controller bus is treated as a P-bus: only active power balance is fixed for that bus. The reactive power flowing at side i on line $(i,j)$ is fixed by the control (it could be at side j too). To resume:
- At controller bus $b_1$:
    - $P_{b_1}^{in} = \sum_{j \in v(b_1)} p_{b_1,j}$.
- At controlled branch $(i,j)$:
    - $q_{i,j} = Q^{c}_{b_1}$.
    
#### Local voltage control for a static var compensator with a slope 

We only support the simple case where:
- Only one generator controlling voltage is connected to a bus. If other generators are present, they should have a local reactive power control ;
- The control is local ;
- No other generators from other controller buses are controlling the bus where the static var compensator is connected. Let's call it $b_1$.

In that case only, the voltage equation at bus $b_1$ is replaced with:

$$v_{b_1} + s \cdot q_{svc} = V^{c}_{b_1}$$

Where $s$ is the slope of the static var compensator.

### Computing HVDC power flow

#### LCC converters

LCC converters can be assimilated to loads in the load flow. Indeed, on one side of the line is the rectifier station, and on the other side of the line is the inverter station. 
The active power flows from the rectifier station to the inverter station, is fixed, and equals to a target value $P$ (AC side). The active power flow at each station at AC side is given by:
  - $P_{rectifier}= P$
  - $P_{inverter}= (1 - loss_{inverter}) * ((1 - loss_{rectifier}) * (P - P_{LineLoss}))$

Power flows are in load convention, the active power at the rectifier side is positive and the active power at the inverter side is negative. The losses are described in the paragraph below.

The reactive power flow on each side of the line (AC side) is determined by the power factor of the converter station, ratio between active power $P$ and apparent power $S$. For each converter, its target reactive power is given by:
  - $Q=\mid P*\tan(\cos(powerfactor))\mid$

#### VSC converters

VSC converters are self-commutated converters that can be assimilated to generators in the loadflow. There can be two main modes to control active power flow through the line:
- In **active power setpoint** mode, as for LCC converters, on one side of the line is the rectifier station, and on the other side of the line is the inverter station. The active power flow
from the rectifier station to the inverter station is fixed and equals to a target value $P$ (AC side). The active power flow at each station at AC side is given by the formula below (losses are described in the next paragraph):
  - $P_{rectifier}= P$
  - $P_{inverter}= (1 - loss_{inverter}) * ((1 - loss_{rectifier}) * (P - P_{LineLoss}))$

- In **AC emulation** mode, the active power flow between both stations is given by: $P = P_0 + k~(\theta_1 - \theta_2)$ 
with $\theta_1$ and $\theta_2$ being the voltage angles at the bus connection for each converter station, and $P_0$ and $k$ being fixed parameters for the HVDC line. 
If $P$ is positive, the converter station 1 is controller, else it is converter station 2. For example, if station 1 is controller, the
active power flow at each station is given by the formula below (losses are described in the next paragraph):
  - $P_{controller} = P_0 + k~(\theta_1 - \theta_2)$
  - $P_{noncontroller} = (1 - loss_{noncontroller}) * ((1 - loss_{controller}) * (P_0 + k~(\theta_1 - \theta_2) - P_{LineLoss}))$

In both cases the target value $P$ (either in active power setpoint mode or in AC emulation mode) is bounded by a maximum active power $P_{max}$ that can possibly be different from one direction to another.

The reactive power flow on each side of the line depends on the enabling of voltage regulation of the converters. If the voltage regulation is enabled, then the VSC converter acts as a generator regulating the voltage. 
Otherwise, reactive power of the converter at AC side is given by its reactive power setpoint.

#### Computing loss in the HVDC line

In both cases of LCC and VSC, the power flows are impacted by losses of the converter stations. In addition, Joule effect (due to resistance in cable) implies line loss in the HVDC line.
This line loss is calculated with the nominal voltage: $P_{LineLoss} = Ri^2$ with $i = P_1 / V$ with $R$ being the cable resistance, $P_1$ being the active power at the output of the controller
station and $V$ being the HVDC nominal voltage (equals 1 per unit).

## DC flows computing

The DC flows computing relies on several classical assumptions to build a model where the active power flowing through a line depends linearly on the voltage angles at its ends.
In this simple model, reactive power flows and active power losses are totally neglected. The following assumptions are made to ease and speed the computations:
- The voltage magnitude is equal to $1$ per unit at each bus,
- The series conductance $G_{i,j}$ of each line $(i,j)$ is neglected, only the series susceptance $B_{i,j}$ is considered,
- The voltage angle difference between two adjacent buses is considered as very small.

Therefore, the power flows from bus $i$ to bus $j$ following the linear expression:

$$ P_{i,j} = \frac{\theta_i-\theta_j+A_{i,j}}{X_{i,j}} $$

Where $X_{i,j}$ is the serial reactance of the line $(i,j)$, $\theta_i$ the voltage angle at bus $i$ and $A_{i,j}$ is the phase angle shifting on side $j$.

DC flows computing gives a linear grid constraints system.
The variables of the system are, for each bus, the voltage angle $\theta$.
The constraints of the system are the active power balance at each bus, except for the slack bus.
The voltage angle at slack bus is set to zero.
Therefore, the linear system is composed of $N$ variables and $N$ constraints, where $N$ is the number of buses in the network.

We introduce the linear matrix $J$ of this system that satisfies:

If $i$ is the slack bus,

$$J_{i,i} = 1$$

Else:

$$J_{i,i} = \sum_{j \in \delta(i)} \frac{1}{X_{i,j}}$$

$$J_{i,j} = - \frac{1}{X_{i,j}}, \quad \forall j \in \delta(i)$$ 

where $\delta(i)$ is the set of buses linked to bus $i$ in the network graph. All other entries of $J$ are zero. 

The right-hand-side $b$ of the system satisfied:

If $i$ is the slack bus,

$$b_{i} = 0$$

Else:

$$b_{i} = P_i - \sum_{j \in \delta(i)} \frac{A_{i,j}}{X_{i,j}}$$

where $\delta(i)$ is the set of buses linked to bus $i$ in the network graph.

Where $P_i$ is the injection at bus $i$.

This linear system is resumed by:

$$ J\theta = b $$

The grid constraints system takes as variables the voltage angles.
Note that the vector $b$ of right-hand sides is linearly computed from the given injections and phase-shifting angles.

To solve this system, we follow the classic approach of the LU matrices decomposition $J = LU$.
Hence, by solving the system using LU decomposition, you can compute the voltage angles by giving as data the injections and the phase-shifting angles.
