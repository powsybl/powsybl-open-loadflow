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

The resulting non-linear system of equations is solved by default via the Newton-Raphson algorithm.
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

LCC converters can be considered as fixed loads in the load flow. Indeed, on one side of the line is the rectifier station, and on the other side of the line is the inverter station. 
The active power flows from the rectifier station to the inverter station, is fixed, and equals to a target value $P$ (AC side). The active power flow at each station at AC side is given by:
  - $P_{rectifier}= P$
  - $P_{inverter}= (1 - LossFactor_{inverter}) * ((1 - LossFactor_{rectifier}) * (P - P_{LineLoss}))$

Power flows are in load sign convention, the active power at the rectifier AC terminal is positive and the active power at the inverter AC terminal is negative.
The HVDC line losses $P_{LineLoss}$ are described in a dedicated section further below.

The reactive power consumption of each converter on AC side is determined by the configured converter power factor of the converter station in the grid model, representing the ratio between active power $P$ and apparent power $S$.
For each converter, its target reactive power is given by:
  - $Q=\mid P*\tan(\cos(powerfactor))\mid$

Note that LCC converters are always absorbing reactive power.

#### VSC converters

VSC converters are self-commutated converters that can be assimilated to generators in the loadflow. There can be two main modes to control active power flow through the line:
- In **active power setpoint** mode, as for LCC converters, on one side of the line is the rectifier station, and on the other side of the line is the inverter station. The active power flow
from the rectifier station to the inverter station is fixed and equals to a target value $P$ (AC side). The active power flow at each station at AC side is given by:
  - $P_{rectifier}= P$
  - $P_{inverter}= (1 - LossFactor_{inverter}) * ((1 - LossFactor_{rectifier}) * (P - P_{LineLoss}))$

- In **AC emulation** mode, the active power flow between both stations is given by: $P = P_0 + k~(\theta_1 - \theta_2)$ 
with $\theta_1$ and $\theta_2$ being the voltage angles at the bus connection for each converter station, and $P_0$ and $k$ being fixed parameters for the HVDC line. 
If $P$ is positive, the converter station 1 is controller, else it is converter station 2. For example, if station 1 is controller, the
active power flow at each station is given by the formula below (HVDC line losses are described in the next paragraph):
  - $P_{controller} = P_0 + k~(\theta_1 - \theta_2)$
  - $P_{noncontroller} = (1 - LossFactor_{noncontroller}) * ((1 - LossFactor_{controller}) * (P_0 + k~(\theta_1 - \theta_2) - P_{LineLoss}))$

The HVDC line losses are described in a dedicated section further below.

In both control modes (active power setpoint mode or in AC emulation mode), the target value $P$ is bounded by a maximum active power $P_{max}$ that can be either:
- the `maxP` configured for the HVDC line,
- or alternatively separate limit values for both directions using the [HVDC operator active power range iIDM extension](inv:powsyblcore:*:*:#hvdc-operator-active-power-range-extension)

The reactive power flow on each side of the line depends on whether voltage regulation of the converters is enabled. If the voltage regulation is enabled, then the VSC converter behaves like a generator regulating the voltage. 
Otherwise, reactive power of the converter at AC side is given by its reactive power setpoint.

#### HVDC line losses

In both cases of LCC and VSC, the power flows are impacted by losses of the converter stations. In addition to the converter losses, Joule effect (due to resistance in cable) implies line losses in the HVDC line.
The HVDC line losses are calculated assuming nominal DC voltage: $P_{LineLoss} = Ri^2$ with $i = P_1 / V$ with $R$ being the HVDC line resistance, $P_1$ being the active power at the output of the controller
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

## Area Interchange Control

Area Interchange Control consists in having the Load Flow finding a solution where area interchanges are solved to match the input target interchange values. It is supported for both AC and DC Load Flow computations.

The area interchange control feature is optional, can be activated via the [parameter `areaInterchangeControl`](parameters.md)
and is performed by an outer loop.

Area Interchange Control is performed using an outer loop, similar in principle to the traditional `SlackDistribution` outer loop.
However unlike the `SlackDistribution` outer loop which distributes imbalance over the entire synchronous component (island),
the Area Interchange Control outer loop performs an active power distribution over areas
(filtered on areas having their type matching the configured [parameter `areaInterchangeControlAreaType`](parameters.md)),
in order to have all areas' active power interchanges matching their target interchanges.

The Area Interchange Control outer loop can handle networks where part (or even all) of the buses are not in an area.
For networks that have no areas at all, the behaviour is the same as with the distributed slack outer loop - in such case
internally the Area Interchange Control outer loop just triggers the Slack Distribution outer loop logic.

Just like other outer loops, the Area Interchange Control outer loop checks whether area imbalance must be distributed:
* If no, the outer loop is stable
* If yes, the outer loop is unstable and a new Newton-Raphson is triggered

### Area Interchange Control - algorithm description

The active power is distributed separately on injections (as configured in the [parameter `balanceType`](parameters.md)) of each area
to compensate the area "total mismatch" that is given by:

$$
Area Total Mismatch = Interchange - Interchange Target + Slack Injection
$$

Where:  
* "Interchange" is the sum of the power flows at the boundaries of the area (load sign convention i.e. counted positive for imports).  
* "Interchange Target" is the interchange target parameter of the area.  
* "Slack Injection" is the active power mismatch of the slack bus(es) present in the area (see [Slack bus mismatch attribution](#slack-bus-mismatch-attribution)). 

The outer loop iterates until this mismatch is below the configured [parameter `areaInterchangePMaxMismatch`](parameters.md) for all areas.

When it is the case, "interchange only" mismatch is computed for all areas:

$$
Interchange Mismatch = Interchange - Interchange Target
$$

If this mismatch for all areas and the slack injection of the buses without area are below the configured [parameter `slackBusPMaxMismatch`](parameters.md), then the outer loop declares a stable status, meaning that the interchanges are correct and the slack bus active power is distributed.

If not, the remaining slack bus mismatch is first distributed over the buses that have no area.

If some slack bus mismatch still remains, it is distributed over all buses of the network.

### Areas validation
There are some cases where areas are considered invalid and will not be considered for the area interchange control:
- Areas without interchange target
- Areas without boundaries
- Areas that have boundaries in multiple synchronous/connected components. If all the boundaries are in the same component but some buses are in different components, only the part in the component of the boundaries will be considered.

In such cases the involved areas are not considered in the Area Interchange Control outer loop, however other valid areas will still be considered.

### Interchange flow calculation

In iIDM each area defines the boundary points to be considered in the interchange. iIDM supports two ways of modeling area boundaries:
- either via an equipment terminal,
- or via a DanglingLine boundary.

In the DanglingLine case, the flow at the boundary side is considered as it should be, for both unpaired DanglingLines and DanglingLines paired in a TieLine.

### Slack bus mismatch attribution
Depending on the location of the slack bus(es), the role of distributing the active power mismatch will be attributed based on the following logic:
- If the slack bus is part of an area: the slack power is attributed to the area (see "total mismatch" calculation in [Algorithm description](#area-interchange-control---algorithm-description)).
Indeed, in this case the slack injection can be seen as an interchange to 'the void' which must be resolved.
- Slack bus has no area:
    - Connected to other bus(es) without area: treated as the slack mismatch of the buses without area
    - Connected to only buses that have an area:
        - All connected branches are boundaries of those areas: Not attributed to anyone, the mismatch will already be present in the interchange mismatch
        - Some connected branches are not declared as boundaries of the areas: Amount of mismatch to distribute is split equally among the areas (added to their "total mismatch")

### Zero impedance boundary branches
The following applies when the [`lowImpedanceBranchMode`](parameters.md) is set to `REPLACE_BY_ZERO_IMPEDANCE_LINE`.
Currently, computations involving zero-impedance branches used as boundary branches are not supported.
However, it is still possible to submit network models that include zero-impedance boundary branches.  
If a terminal of a zero-impedance branch is designated as a boundary, Open LoadFlow will internally assign the branch
an impedance value equal to the [`lowImpedanceThreshold`](parameters.md) parameter.

## Fast-Decoupled Algorithm
Fast-Decoupled is an algorithm to solve the inner-loop of the load flow problem, like the Newton-Raphson one.
It is activated giving the `FAST_DECOUPLED` value to the [`acSolverType`](parameters.md) parameter.
The solved equation system is the same as the one solved by Newton-Raphson method.
However, the Jacobian matrix used is decomposed into two smaller matrices, decoupling the active power balance equations from voltage magnitudes variations and reactive power balance equations from voltage phases variations.

Note that "Fast-Decoupled" is the academic name for this algorithm and not a statement about its performances versus the Newton-Raphson algorithm.

### Method
The Fast-Decoupled method is composed of two parts, one relative to the decoupling, another relative to the speeding of the calculations.

The decoupling is obtained by dividing the Jacobian matrix into two matrices, one relative to voltage phases variables and active power equations, the other relative to voltage magnitudes variables and reactive power equations.
Combining those two smaller matrices, one can obtain an approximation of the real Jacobian, where some terms have been discarded.

By approximating more terms on top of the Jacobian structure simplification,
the algorithm provides two Jacobian matrices that are constant, with respect to the multiplication by a diagonal matrix.
Thus, the LU decomposition of the two Jacobian matrices is done only once, at the start of the first Fast-Decoupled iteration.

Regarding state vector scaling, the Fast-Decoupled uses both personalized max voltage change and line-search routines.
Without these routines, the algorithm struggles to converge on realistic large networks, as it has a simplified vision of the impact of the system variables.
Note that [`stateVectorScalingMode`](parameters.md) is not taken into account.

### Limitations
The current implemented version cannot compute when one of the following parameter is activated:
- [`asymmetrical`](parameters.md),
- [`hvdcAcEmulation`](inv:powsyblcore:*:*#simulation/loadflow/configuration)

In case where the user has selected both the Fast-Decoupled algorithm and one of this parameter, an exception is triggered.

Users should notice that default parameters are optimized for the Newton-Raphson algorithm, as it is the default one.
When the Fast-Decoupled algorithm is used, we recommend these values for some convergence parameters:
- [`maxNewtonRaphsonIterations`](parameters.md): 75,
- [`lineSearchStateVectorScalingMaxIteration`](parameters.md): 4,
- [`lineSearchStateVectorScalingStepFold`](parameters.md): `3/2 = 1.5`.

## AC DC flows computing

AC DC lows computing in OpenLoadFLow is similar to AC flows computing, but with AC and DC equations in the same system.
So the unknowns are voltage magnitude and phase angle for each AC bus, and voltage for each DC node, active and reactive 
power for each voltage source converter.
Concerning AC side, the equations are the same as in AC flows computing, concerning DC side, the equations induced by DC
components are the followings:

#### DC node

At least one DC node must be connected to the ground in each DC network, its potential is set to 0. This equation sets its potential :
$V = 0$
For the others, each DC node introduces an equation of current balance: $\sum_{i} I_i = 0$ where $I_i$ are the terms 
introduced by the DC components connected to the DC node.

#### DC Line

Each DC line adds one term in both of its two connected DC nodes current balance:

$\sum_{i} I_i - \frac{V_1 - V_2}{R}= 0$ for dcNode1

$\sum_{i} I_i + \frac{V_1 - V_2}{R}= 0$ for dcNode2

#### Voltage source converters

Let consider a network that is composed of one AC network, and one DC network.
The voltage source converter is the link between AC and DC networks, it is linked to one AC bus from one side, and two 
DC nodes from the other side. At least one of the voltage source converters of the DC network must control the voltage 
between its two dc nodes, the other voltage source converters control the Power injected in the AC network.

$P_{AC}$, is the power flow injected by the converter to the AC
side. So $P_{AC}<0$ if the power flows from AC to DC and $P_{AC}>0$ in the contrary.

If the converter is in `P_PCC` control Mode, we add an equation to impose $P_{AC}$ :

$P_{AC}$ = $P_{Ref}$

Else if the converter is in `V_DC` control Mode, we add an equation to impose the voltage between its two DC nodes :

$V_{1} - V_{2} = V_{Ref}$

We add the Power injected by the Converter to AC side :

$\sum_{i} P_i + P_{AC} = 0$

We add an equation to ensure the conservation of power between AC and DC. We introduce the variable $I_{Conv}$
which is the current flowing in the converter from dcNode1 to dcNode2. Here, we have $P_{DC} = -P_{AC} - P_{Loss}$
which is the Power injected by the converter to the AC side, and $P_{Loss}$ the converter losses depending on AC
current.

$P_{DC} = I_{Conv}*(V_1-V_2)$

Then, we add this power to the current balances of dcNode1 and dcNode2

$\sum_{i} I_i + I_{Conv} = 0$ for dcNode1

$\sum_{i} I_i - I_{Conv}= 0$ for dcNode2

#### Power Equations

At AC side, the voltage source converter imposes its power flow $P_{AC}$ like a generator, and it can be set in two
modes :

- Reactive Power control mode, in which it imposes the Reactive Power injected from AC to DC, which is 0 by default.
  And the AC voltage is not fixed.
- Voltage regulator control mode, in which it imposes the voltage at its AC Bus. So the reactive Power is not fixed.

The Power injected in the DC network is thus calculated, including losses due to the conversion. 

The loss is defined as $P_{Loss}>0$, which calculation is explained later.

The Power injected by the Converter from AC side to DC side is:

$$
P_{DC} = -P_{AC} - P_{Loss}
$$

If the converter acts as Rectifier, AC injects Power in DC, thus $P_{DC}>0$ and $P_{AC}<0$, so we have :

$$
|P_{DC}| = -P_{AC} - P_{Loss}
$$
$$
|P_{DC}| = |P_{AC}| - P_{Loss}
$$

And if the converter acts as Inverter, DC injects Power in AC, thus $P_{DC}<0$ and $P_{AC}>0$, so we have :

$$
|P_{DC}| = P_{AC} + P_{Loss}
$$
$$
|P_{AC}| = |P_{DC}| - P_{Loss}
$$

In both cases, we do have a loss of power when passing through the converter.

#### Loss Calculation

The loss
calculation is inherited from this paper : https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=5275450.

$P_{Loss}$ is defined as :
$
P_{Loss} = Loss_{A} + Loss_{B}*I_{AC,pu} + Loss_{C}*I_{AC,pu}^{2}
$
Where $Loss_{A}, Loss_{B}$ and $Loss_{C}$ are loss factors that depend on the converter, also called `idle loss`,
`switching loss` and `resistive loss`, and $I_{ACpu}$ is the current flowing from the converter to AC side.

$I_{AC,pu}$ is calculated by :

$$
\begin{aligned}
I_{AC,pu} &= \frac{\sqrt{Q_{AC,pu}^{2} + P_{AC,pu}^{2}}}{V_{AC,pu}} \\
\end{aligned}
$$

And in the Jacobian, we then have the two derivatives: $\frac{\partial P_{DC,pu}}{\partial P_{AC,pu}}$ and
$\frac{\partial P_{DC,pu}}{\partial Q_{AC,pu}}$ :

$$
\begin{aligned}
\frac{\partial P_{DC,pu}}{\partial P_{AC,pu}} &= -1- \frac{\partial P_{Loss,pu}}{\partial P_{AC,pu}} \\
&=-1-\frac{\partial}{\partial P_{AC,pu}}(Loss_{A} + Loss_{B}*I_{AC,pu} + Loss_{C}*I_{AC,pu}^{2}) \\
&= -1-(Loss_{B}*\frac{\partial I_{AC,pu}}{\partial P_{AC,pu}}+2I_{AC,pu}Loss_{C}\frac{\partial I_{AC,pu}}{\partial P_{AC,pu}}) \\
&= -1-(Loss_{B}+2I_{AC,pu}Loss_{C})*\frac{\partial I_{AC,pu}}{\partial P_{AC,pu}}\\
&= -1-\frac{P_{AC,pu}(Loss_{B} + 2Loss_{C}*I_{AC,pu})}{\sqrt{Q_{AC,pu}^{2} + P_{AC,pu}^{2}}}
\end{aligned}
$$

And by the same calculation :

$$
\begin{aligned}
\frac{\partial P_{DC,pu}}{\partial Q_{AC,pu}} &= - \frac{\partial P_{Loss,pu}}{\partial Q_{AC,pu}} \\
&= \frac{-Q_{AC,pu}(Loss_{B} + 2Loss_{C}*I_{AC,pu})}{\sqrt{Q_{AC,pu}^{2} + P_{AC,pu}^{2}}}
\end{aligned}
$$




