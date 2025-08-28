# VSC Converter Modeling and equations

## Modeling

Let consider a Network that is composed of one AC network, and one DC Network.
The VSC Converter is the link between AC and DC networks, it is linked to one AC Bus from one side, and one DC Node
from the other side. One of the VSC Converters of the DC Network must control the Tension of its DC Node, it acts like
a slack bus, the others VSC Converters control the Power injected in the AC Network.

## Power Equations

At AC side, the VSC Converter imposes the Power $P_{AC}$ like a generator, and it can be set into two modes :

- Reactive Power control mode, in which it imposes the Reactive Power injected from AC to DC, which is 0 by default.
  And the AC Tension is not fixed.
- Tension control mode, in which it imposes the tension at its AC Bus. So the reactive Power is not fixed.

The Power injected in the DC Network is thus calculated, including losses due to the conversion. The loss is
defined as $P_{Loss}>0$, which calculation is explained later.

The Power injected by the Converter from AC side to DC side is:

$$
P_{DC} = -P_{AC} - P_{Loss}
$$

If the Converter acts as Rectifier, AC injects Power in DC, thus $P_{DC}>0$ and $P_{AC}<0$, so we have :

$$
|P_{DC}| = -P_{AC} - P_{Loss}
$$
$$
|P_{DC}| = |P_{AC}| - P_{Loss}
$$

And if the Converter acts as Inverter, DC injects Power in AC, thus $P_{DC}<0$ and $P_{AC}>0$, so we have :

$$
|P_{DC}| = P_{AC} + P_{Loss}
$$
$$
|P_{AC}| = |P_{DC}| - P_{Loss}
$$

In both cases, we do have a loss of power when passing through the converter.

## Loss Calculation

The loss
calculation is inherited from this paper : https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=5275450.

$P_{Loss}$ is defined as :
$$
P_{Loss} = Loss_{A} + Loss_{B}*I_{AC,pu} + Loss_{C}*I_{AC,pu}^{2}
$$
Where $Loss_{A}, Loss_{B}$ and $Loss_{C}$ are loss factors that depend on the Converter, and $I_{ACpu}$ is the
current flowing from AC side to the converter, or from the converter to AC side depending on the converter mode.

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

