# Modelling and equations

## DC sensitivity analysis

A DC sensitivity analysis starts from the DC flows computing described in the [load flow section](../loadflow/loadflow.md#dc-flows-computing). Simple sensitivity analyses are supported as:
- How an injection increase of 1 MW will impact the flow of a branch ;
- How a phase shifting of 1° of a phase tap changer will impact the flow of a branch.

This could be done in a set of branches given by the user.

### Sensitivities on the base network 

Injection increases could be either an active power increase of a generator $targetP$ or an active power increase of a load $P0$. To get the impact of an injection increase in a given bus into a given branch, we compute the right-hand side $b$ corresponding to an injection of 1 MW at this bus and 0 MW elsewhere. The LU decomposition gives the matrices $L$ and $U$ in order to compute the vector $\theta$. Then it is easy to retrieve the active power flow in the branch.

For example, to get the sensitivity from injection increase in bus $i$ to branch $(k,l)$, we compute $b$ satisfying:

$$
\begin{align}
\texttt{if}~n=i:&\\
&b_{n} = 1\\
\texttt{else}:&\\
&b_{n} = 0
\end{align}
$$

Then, we retrieve the sensitivity with $\theta$ calculating:

$$ s_{i,kl} = \frac{\theta_k-\theta_l}{X_{k,l}} $$

To get the sensitivity from phase-shifting angle in a given branch to a given branch, we compute the right-hand side $b$ corresponding to an increase of 1° for the phase-shifting angle at this branch and 0 elsewhere. The LU decomposition gives the matrices $L$ and $U$ in order to compute the vector $\theta$. Then it is easy to retrieve the active power flow in the branch.

For example, to get the sensitivity from phase-shifting angle increase in branch $(i,j)$ to branch $(k,l)$, we compute $b$ satisfying:

$$
\begin{align}
\texttt{if}~n=i:&\\
&b_{n} = -\frac{\pi}{180X_{i,j}}\\
\texttt{if}~n=j:&\\
&b_{n} = \frac{\pi}{180X_{i,j}}\\
\texttt{else}:&\\
&b_{n} = 0
\end{align}
$$
	
Then, we retrieve the sensitivity with $\theta$ calculating:

$$ s_{ij,kl} = \frac{\theta_k-\theta_l}{X_{k,l}} $$

In the special case where $(i,j)=(k,l)$, we retrieve the sensitivity calculating:

$$ s_{ij,kl} = \frac{\theta_k-\theta_l + \frac{\pi}{180}}{X_{k,l}} $$

### Sensitivities in case of slack distribution

If active power mismatch is distributed on loads or on generators, an injection increase at bus $b$ will be distorted by slack distribution. Thus, the sensitivity analysis must include the sensitivity value correction linked to the effects of the participation units to the slack distribution. Let's introduce the list of participating units $(g \in U)$ involved in the slack distribution and their respective participation factor $(r^c_g)$. Note that at this stage, this list of participating elements is computed from the initial network state and not from its state after the DC flows computation. The new sensitivity values are computed from the base case sensitivity values through the following formula:

$$
s_{b,kl}^c = s_{b,kl} - \sum_{g \in U} r^c_g s_{g,kl}
$$

We only support for the moment balance type `PROPORTIONAL_TO_GENERATION_P_MAX` and `PROPORTIONAL_TO_LOAD`.

### Contingency management

The contingency management consists in calculating the sensitivity values for post-contingency states of the network. A post-contingency state of the network is the state of the network after an outage (most of the time, the loss of a line). In the particular case of DC flows approximation, the post-contingency sensitivity values can be computed using the pre-contingency sensitivity values and some flow transfer factors. Thus, the same LU decomposition is used both for the pre-contingency analysis and for the post-contingency analysis.

#### Loss of a single branch

The most frequent event in the network is the loss of a single branch (including the loss of a transformer with or without tap changer).
 
Let's introduce $s_{b,ij,mk}$ the sensitivity of an increase of 1 MW at bus $b$ on branch $(i,j)$ where the branch $(m,k)$ represents the outage.
We want to compute this sensitivity.

We call $b^1$ the right-hand side vector corresponding to an increase of 1 MW at bus $b$. We call $\theta^1$ the state vector of voltage angles obtained by solving the equation system on the pre-contingency network that has as right-hand side $b^1$.

We call $b^2$ be the right-hand side vector corresponding to an increase of 1 MW at bus $m$ and a decrease of 1 MW at bus $k$. We call $\theta^2$ the state vector of voltage angles obtained by solving the equation system on the pre-contingency network that has as right-hand side $b^2$.

Note that both $\theta^1$ and $\theta^2$ are built using the same LU decomposition of the constraints matrix $J$.

Let $s_{b,ij}$ be the sensitivity of an increase of 1 MW at bus $b$ on branch $(i,j)$ on the pre-contingency network, we recall that:

$$
s_{b,ij} = \frac{\theta^1_i-\theta^1_j}{X_{i,j}}
$$

Let $s_{mk,ij}$ be the sensitivity of an increase of 1 MW at bus $m$ and a decrease of 1 MW at bus $k$, on the pre-contingency network.
It can be easily computed through the formula, valid for all buses:

$$
s_{mk,ij} = \frac{\theta^2_i-\theta^2_j}{X_{i,j}}
$$

Then, the post-contingency sensitivity $s_{b,ij,mk}$ satisfies:

$$
s_{b,ij,mk} = s_{b,ij} + \frac{\theta^1_m-\theta^1_k}{X_{m,k} - (\theta^2_m-\theta^2_k)}s_{mk,ij}
$$

#### Loss of more than one branch

Sometimes, an event in the network causes the loss of several buses and branches. Connected to these lost buses, we can have generators or loads.
 
Let's introduce $s_{b,ij,E}$ the sensitivity of an increase of 1 MW at bus $b$ on branch $(i,j)$ when the event $E$ occurs. The event $E$ corresponds to the loss of the $n$ branches indexed by $(m_1,k_1), \cdots, (m_n,k_n)$. We want to compute this sensitivity.

We call $b^1$ the right-hand side vector corresponding to an increase of 1 MW at bus $b$. We call $\theta^1$ the state vector of voltage angles obtained by solving the equation system on the pre-contingency network that has as right-hand side $b^1$.

We call $b^{p+1}$ be the right-hand side vector corresponding to an increase of 1 MW at bus $m_p$ and a decrease of 1 MW at bus $k_p$. We call $\theta^{p+1}$ the state vector of voltage angles obtained by solving the equation system on the pre-contingency network that has as right-hand side $b^{p+1}$.

Then, the post-contingency sensitivity $s_{b,ij,E}$ satisfies:

$$
s_{b,ij,E} = s_{b,ij} + \sum_{p=1}^n \alpha_p s_{m_pk_p,ij}
$$

Where, valid for all buses:

$$
s_{m_pk_p,ij} = \frac{\theta^{p+1}_i-\theta^{p+1}_j}{X_{i,j}}
$$

The vector of coefficients $\alpha$ is computed as the solution of a linear system of size $n$:

$$
Mx = c
$$

Where $M$ is the $n \times n$ matrix defined by:

$$
\begin{align}
M_{p,q} =& -(\theta^{q+1}_{m_p} - \theta^{q+1}_{k_p}) & \texttt{if}~p \neq q,\\
M_{p,q} =& X_{m_p,k_p} - (\theta^{p+1}_{m_p} - \theta^{p+1}_{k_p})& \texttt{else}.
\end{align}
$$

And $c$ the vector of size $n$ defined by:

$$
c_p = \theta^1_{m_p} - \theta^1_{k_p}
$$

#### Loss of network connectivity management

An event can create one or more new synchronous component. In case of the loss of a single branch (when n = 1), it means, mathematically, that the factor $X_{m,k} - (\theta^2_m-\theta^2_k)$ equals to $0$. In case of the loss of more than one branch (when n > 1), it means, mathematically, that the matrix $M$ previously defines is not invertible. In that special configuration, previously described coefficients $\alpha$ cannot be computed and used to assess post-contingency sensitivities. Most of the time, the secondary networks are out of voltage, but it is still possible to get the sensitivity values in the largest network containing the slack bus. In real world, it is like the initial network has lost small parts that not contain the slack bus. Thus, the sensitivity values can still be computed.
 
A sensitivity involves two equipments in the network: a load or a generator, etc. connected to a bus, or a phase tap changer and a monitored branch. These two equipments should be in the same connected component. 

##### Loss of connectivity by a single branch

This case is quite simple to support. Sensitivities computed on the base network are still valid for the post-contingency network as long as they refer to equipments in the connected component containing the slack bus.

##### Loss of connectivity by more than one branch

A post-contingency network can be divided into several largest connected components. Let's introduce $t$ the number of those components.

Obviously: $t \leq n+1$

One of this component contains the slack bus and should be the largest. Only sensitivities involving equipments of that component can be computed. A easy way to compute them is to connect lines that have been disconnected during the contingency. More precisely, we have to find $t-1$ lines to reconnect in order to obtain a single connected component. As we reconnect exactly $t-1$ lines and obtain a connected network, we are assured that there are no loop in it.

As the two equipments of the sensitivity lie in the largest connected component of the post-contingency network (containing the slack bus), no flow can pass through the reconnected lines. Then, the sensitivities of the post-contingency network are equal to those of the connected network, where the matrix $M$ is invertible.

##### How to detect a loss of connectivity?

It might be quite time-consuming to assess the connectivity of the post-contingency network using classic graph theory tools. Here we propose another way to detect a loss of connectivity in the post-contingency network. We need to compute the $\theta^p$ vectors as described in the section describing the loss of more than one branch. Our method uses those vectors to detect a loss of connectivity.

First, we define and compute:

$$
\forall p \in \{1,\dots,n\}, \quad \sigma_p = \sum_{q \in \{1,\dots,n\}} \left|\frac{\theta^{p+1}_{m_q}-\theta^{p+1}_{k_q}}{X_{m_q,k_q}}\right|
$$

If we can find $p \in \{1,\dots,n\}$ such as $\sigma_p \geq 1$, then, there is probably a loss of connectivity in the post-contingency network. Else, we are sure that there is no connectivity loss in the post-contingency network.

We compute sequentially the $\sigma_p$ coefficients and, as soon as one of them is found greater or equal to $1$, we stop the process and a classic graph analysis is performed. Most of the time, connectivity is not lost, especially when we loose a single branch ($\sigma_1$ is equal to $1$).

##### Slack distribution in case of connectivity loss

In case of connectivity loss, participating elements that are not connected to slack bus are removed from the list of initial participating elements.
Then, the participating factor of remaining elements is increased such as their sum remains equal to one.

#### Extension to reference flow computations

The methodology described below to access to sensitivity values in a post-contingency network can be applied to compute reference flows in the same post-contingency network.

In that case, the vector of right-hand side $b_1$ is the injections at network buses. All other data or formula are unchanged. Beware that the system $Mx = c$ whose vector $\alpha$ is solution is modified when $b_1$ is modified, because right-hand side vector $c$ depends on $\theta_1$. However matrix $M$ only depends on which lines are disconnected by the outage.

In case of an outage causing connectivity loss in the post-contingency network, reference flows can be computed only in the largest connected component containing the slack bus. In that case, the right-hand side injection vector $b_1$ is modified to take into account only injections in the largest connected part of the post-contingency network (all other injections are set to zero). The same methodology of reconnecting some lines to obtain a connected network is used too.

#### Return codes in case of uncomputable sensitivities

If an user asks for a sensitivity computation which is not possible to perform, Open Load Flow provides different return codes which are listed below:
- If the user requests a sensitivity involving a variable or a function which does not exist in the network: an error is thrown, Open Load Flow terminates.
- If the user requests a sensitivity involving a variable which does not belong to the main connected component after a connectivity loss: a warning is displayed and the sensitivity is not computed.
- If the user requests a sensitivity involving a function which does not belong to the main connected component of after a connectivity loss: the sensitivity is equal to $0$. Note that if both variable and function do not belong to the main connected component (where we have the slack bus), the priority is given to the variable: a warning is displayed and the sensitivity is not computed.


## AC sensitivity analysis

An AC sensitivity analysis starts from the AC flows computing described in the [load flow section](../loadflow/loadflow.md#ac-flows-computing). Simple sensitivity analyses are supported as:
- How an active injection increase of 1 MW will impact the flow of a branch ;
- How an active injection increase of 1 MW will impact the current of a branch ;
- How a phase shifting of 1° of a phase tap changer will impact the flow of a branch ;
- How a phase shifting of 1° of a phase tap changer will impact the current of a branch.

This could be done in a set of branches given by the user.

Every sensitivity computation is done using the following formula:

$$ S_{\eta,p}(v,\phi) = g_p^T(v,\phi) + G_{v,\phi}(v,\phi)^TJ(v,\phi)^{-1}f_p(v,\phi)$$

where:
- $\eta$ is the list of values measured by the sensitivity (i.e. flow on a line or current on a line) ;
- $p$ is the parameter whose variation is studied by the sensitivity ;
- $S_{\eta,p}(v,\phi)$ is a row-vector of sensitivities on values $\eta$ according to variation of parameter $p$, at the point $(v,\phi)$ ;
- $g_p(v,\phi)$ is the gradient of sensitivities according to the parameter $p$, at the point $(v,\phi)$ ;
- $G_{v,\phi}(v,\phi)$ is the matrix whose each column is the gradient of a sensitivity according to state variables $v$ and $\phi$, at the point $(v,\phi)$ ;
- $J(v,\phi)$ is the jacobian matrix of power flow equations system at the point $(v,\phi)$ ;
- $f_p(v,\phi)$ is the gradient of power flow equations according to the parameter $p$, at the point $(v,\phi)$.

Giving a list of sensitivities $\eta$ and a parameter $p$, an AC sensitivity analysis is done following the steps:
1. Extract from a power flow computation state variables $(v,\phi)$ ;
2. Compute vector $f_p(v,\phi)$ ;
3. Compute jacobian matrix $J(v,\phi)$ ;
4. Compute the LU decomposition of the jacobian ;
5. Compute $J(v,\phi)^{-1}f_p(v,\phi)$ with the LU decomposition ;
6. Compute $G_{v,\phi}(v,\phi)$ ;
7. Compute $g_p(v,\phi)$ ;
8. Compute $S_{\eta,p}(v,\phi)$ using the formula.

Following sections detail how $f_p(v,\phi)$, $G_{v,\phi}(v,\phi)$ and $g_p(v,\phi)$ are computed.

### Computation of vector $f_p(v,\phi)$

Vector $f_p(v,\phi)$ is the gradient of power flow equations according to the parameter $p$, at the point $(v,\phi)$. Its computation depends on if $p$ is the active injection at a bus or the phase shift of a phase tap changer.

#### Case 1: $p$ is the active injection at bus $i$

In this case, all entries of vector $f_p(v,\phi)$ are equal to zero, except one which corresponds to the active power flow balance equation at bus $i$:

$$
\begin{align}
\texttt{if}~k~\texttt{is the active power flow balance at bus}~i:& \quad (f_p(v,\phi))_k = 1,\\
\\
\texttt{else}:& \quad (f_p(v,\phi))_k = 0.
\end{align}
$$

#### Case 2: $p$ is the phase shift of the phase tap changer on side $i$ of line $(i,j)$

In this case, all entries of vector $f_p(v,\phi)$ are equal to zero, except the following ones:
- active power flow balance equation at both buses $i$ and $j$,
- reactive power flow balance equation at bus $i$, only if it is a PQ bus,
- reactive power flow balance equation at bus $j$, only if it is a PQ bus.

$$
\begin{align}
\texttt{let}~\alpha &= -\rho_iv_iY\rho_jv_j\text{cos}(\theta)\frac{\pi}{180},\\
\\
\texttt{let}~\beta &= \rho_iv_iY\rho_jv_j\text{sin}(\theta)\frac{\pi}{180}.
\end{align}
$$

$$
\begin{align}
\texttt{if}~k~\texttt{is the active power flow balance at bus}~i:& \quad (f_p(v,\phi))_k = \alpha,\\
\\
\texttt{if}~k~\texttt{is the active power flow balance at bus}~j:& \quad (f_p(v,\phi))_k = -\alpha,\\
\\
\texttt{if}~k~\texttt{is the reactive power flow balance at bus}~i:& \quad (f_p(v,\phi))_k = \beta,\\
\\
\texttt{if}~k~\texttt{is the reactive power flow balance at bus}~j:& \quad (f_p(v,\phi))_k = -\beta,\\
\\
\texttt{else}:& \quad (f_p(v,\phi))_k = 0.
\end{align}
$$

### Computation of matrix $G_{v,\phi}(v,\phi)$

The matrix $G_{v,\phi}(v,\phi)$ is the matrix which column is the gradient of a sensitivity according to state variables $v$ and $\phi$, at the point $(v,\phi)$. It is computed column by column. The computation of column $l$ depends on if it is relative to the sensitivity of the power or the current flowing from $i$ to $j$.

#### Case 1: Column $l$ is relative to the sensitivity of the power flowing from $i$ to $j$

In this case, all entries of column $l$ of $G_{v,\phi}(v,\phi)$ are equal to zero, except for those corresponding to the voltage magnitudes and angles at both buses $i$ and $j$. Let's introduce the three following derivatives:

$$
\begin{align}
\frac{dp}{dv_i} &= \rho_i(2G\rho_iv_i + 2Y\rho_iv_i\text{sin}(\Xi) - Y\rho_jv_j\text{sin}(\theta))\\
\\
\frac{dp}{dv_j} &= - \rho_iv_iY\rho_j\text{sin}(\theta)\\
\\
\frac{dp}{d\phi_i} &= - \rho_iv_iY\rho_jv_j\text{cos}(\theta)
\end{align}
$$

Note that $p$ always corresponds to side 1 of the branch, it is an arbitrary choice. And note that $\frac{dp}{d\phi_j}$ equals to $-\frac{dp}{d\phi_i}$.

$$
\begin{align}
\texttt{if}~k~\texttt{is the voltage magnitude at bus}~i:& \quad (G_{v,\phi})_{k,l} = \frac{dp}{dv_i},\\
\\
\texttt{if}~k~\texttt{is the voltage magnitude at bus}~j:& \quad (G_{v,\phi})_{k,l} = \frac{dp}{dv_j},\\
\\
\texttt{if}~k~\texttt{is the voltage angle at bus}~i:& \quad (G_{v,\phi})_{k,l} = \frac{dp}{d\phi_i},\\
\\
\texttt{if}~k~\texttt{is the voltage angle at bus}~j:& \quad (G_{v,\phi})_{k,l} = -\frac{dp}{d\phi_i},\\
\\
\texttt{else}:& \quad (G_{v,\phi})_{k,l} = 0.
\end{align}
$$

#### Case 2: Column $l$ is relative to the sensitivity of the current flowing from $i$ to $j$

As previous case, all entries of column $l$ of $G_{v,\phi}(v,\phi)$ equal zero, except these corresponding to the voltage magnitudes and angles at both buses $i$ and $j$. Let's introduce $Re(I)$ and $Im(I)$ respectively the real and imaginary parts of the complex current flowing from $i$ to $j$:

$$
\begin{align}
N_f &= \frac{1000}{\sqrt{3}}, \\
\\
w_i &= \rho_iv_i, \\
\\
w_j &= Y\rho_jv_j, \\
\\
\gamma &= \Xi - A_i + A_j + \phi_j, \\
\\
Re(I) &= N_f \rho_i (w_i (G_i \text{cos}(\phi_i) - B_i \text{sin}(\phi_i) + Y \text{sin}(\Xi + \phi_i)) - w_j \text{sin}(\gamma)), \\
\\
Im(I) &= N_f \rho_i (w_i (G_i \text{sin}(\phi_i) + B_i \text{cos}(\phi_i) - Y \text{cos}(\Xi + \phi_i)) + w_j \text{cos}(\gamma)), \\
\\
|I| &= \sqrt{Re(I)^2 + Im(I)^2}.
\end{align}
$$

Which derivatives are the following ones:

$$
\begin{align}
\frac{dRe(I)}{dv_i} &= N_f \rho_i^2 (G_i \text{cos}(\phi_i) - B_i \text{sin}(\phi_i) + Y \text{sin}(\Xi + \phi_i)), \\
\\
\frac{dRe(I)}{dv_j} &= - N_f \rho_i Y \rho_j \text{sin}(\gamma), \\
\\
\frac{dRe(I)}{d\phi_i} &= N_f \rho_i w_i (-G_i \text{sin}(\phi_i) - B_i \text{cos}(\phi_i) + Y \text{cos}(\Xi + \phi_i)), \\
\\
\frac{dRe(I)}{d\phi_j} &= -N_f \rho_i w_j \text{cos}(\gamma), \\
\\
\frac{dIm(I)}{dv_i} &= N_f \rho_i^2 (G_i \text{sin}(\phi_i) + B_i \text{cos}(\phi_i) - Y \text{cos}(\Xi + \phi_i)), \\
\\
\frac{dIm(I)}{dv_j} &= N_f \rho_i Y \rho_j \text{cos}(\gamma), \\
\\
\frac{dIm(I)}{d\phi_i} &= N_f \rho_i w_i (G_i \text{cos}(\phi_i) - B_i \text{sin}(\phi_i) + Y \text{sin}(\Xi + \phi_i)), \\
\\
\frac{dIm(I)}{d\phi_j} &= -N_f \rho_i w_j \text{sin}(\gamma).
\end{align}
$$

We obtain:

$$
\begin{align}
\texttt{if}~k~\texttt{is the voltage magnitude at bus}~i:& \quad (G_{v,\phi})_{k,l} = \frac{Re(I)\frac{dRe(I)}{dv_i} + Im(I)\frac{dIm(I)}{dv_i}}{|I|},\\
\\
\texttt{if}~k~\texttt{is the voltage magnitude at bus}~j:& \quad (G_{v,\phi})_{k,l} = \frac{Re(I)\frac{dRe(I)}{dv_j} + Im(I)\frac{dIm(I)}{dv_j}}{|I|},\\
\\
\texttt{if}~k~\texttt{is the voltage angle at bus}~i:& \quad (G_{v,\phi})_{k,l} = \frac{Re(I)\frac{dRe(I)}{d\phi_i} + Im(I)\frac{dIm(I)}{d\phi_i}}{|I|},\\
\\
\texttt{if}~k~\texttt{is the voltage angle at bus}~j:& \quad (G_{v,\phi})_{k,l} = \frac{Re(I)\frac{dRe(I)}{d\phi_j} + Im(I)\frac{dIm(I)}{d\phi_j}}{|I|},\\
\\
\texttt{else}:& \quad (G_{v,\phi})_{k,l} = 0.
\end{align}
$$

### Computation of vector $g_p(v,\phi)$

Vector $g_p(v,\phi)$ is the gradient of the sensitivities according to the parameter $p$, at the point $(v,\phi)$. First, its computation depends on if $p$ is the active injection at a bus or the phase shift of a phase tap changer. Secondly it depends on if the sensitivity function is the power or the current flowing through a line $(i,j)$.

In the case of an injection as parameter $p$, vector $g_p(v,\phi)$ equals zero.

In the case of a phase shift of a phase tap changer as parameter $p$, a component of $g_p(v,\phi)$ is non zero if and only if it is relative to a sensitivity on the branch $(i,j)$ where lies the phase tap changer. In this case, the value of the component is given by:

$$
\begin{align}
\rho_iv_iY\rho_jv_j\texttt{cos}(\theta)\frac{\pi}{180},& \quad\texttt{if the sensitivity is on the power flow},\\
\\
-\frac{Re(I)\frac{dRe(I)}{d\phi_j} + Im(I)\frac{dIm(I)}{d\phi_j}}{|I|}\frac{\pi}{180},& \quad\texttt{if the sensitivity is on the current flow}.
\end{align}
$$

Note that the two formulas above are valid when the phase tap changer is on side $i$ of the monitored line $(i,j)$. The opposite values are taken when the equipment is on side $j$ of the line.

### Sensitivities in case of slack distribution

Computations of sensitivities in case of a slack distribution work in the same way as in the [DC sensitivity analysis](#sensitivities-in-case-of-slack-distribution).

Let's introduce the list of participating elements $(g \in U)$ and their respective participation factor $(r^c_g)$. We recall the formula:

$
S_{\eta,p}^c = S_{\eta,p} - \sum_{g \in U} r^c_g S_{\eta,g}.
$$

### Contingency management

Contrary to [DC sensitivity analysis](#contingency-management), computations of sensitivities in case of contingencies are performed by restarting the sequence of computations based on the equation system of the post-contingency network and not more on the equation system of the pre-contingency network.

### Sensitivity involving voltage magnitudes

Open Load Flow allows to compute the sensitivity from an increase of voltage magnitude at a PV-bus to the voltage magnitude at a PQ-bus. This is done using the same formula previously introduced:

$ S_{\eta,p}(v,\phi) = g_p^T(v,\phi) + G_{v,\phi}(v,\phi)^TJ(v,\phi)^{-1}f_p(v,\phi). $

Where:
- $\eta$ is the voltage magnitude at a PQ-bus named $i$,
- $p$ is the voltage magnitude at a PV-bus named $b$,
- $f_p(v,\phi)$ is a vector composed of null entries except for the one relative to the voltage magnitude at PV-bus $b$, that equals $1$,
- $G_{v,\phi}(v,\phi)$ is a vector composed of null entries except for the one relative to the voltage magnitude at PQ-bus $i$, that equals $1$,
- $g_p(v,\phi)$ is null.
