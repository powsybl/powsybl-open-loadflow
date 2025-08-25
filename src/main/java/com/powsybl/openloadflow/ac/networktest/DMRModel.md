The Equations of the DC Multi-Node Representation are the following:

# Converters

A converter is connected to two DC Nodes, one is the Return Node and the other is either positive or
negative Node, we will call it dcNode1.
The converter controls the Power $P_{AC}$, which is the power flow injected by the converter to the AC
side. So $P_{AC}<0$ if the Power flows from AC to DC and $P_{AC}>0$ in the contrary.

## - Control equations

If the converter is in P control Mode, we add an equation to impose $P_{AC}$ :

#### AC_CONV_TARGET_P_REF : $P_{AC}$ = $P_{REF}$

Else if the converter is in V Mode, we add an equation to impose $V_{DC}$ :

#### DC_NODE_TARGET_V_REF : $V_{1} - V_{RETURN} = V_{REF}$

## - AC Equations

We add the Power injected by the Converter to AC side :

#### BUS_TARGET_P : $\sum_{i} P_i + P_{AC} = 0$


## - DC Equations

We add an equation to ensure the conservation of power between AC and DC. We introduce the variable $I_{CONV}$
which is the current flowing in the converter from return to positive/negative layer. Here, we have
$P_{DC} = -P_{AC} - P_{LOSS}$ which is the Power injected by the converter to the AC side

#### CONV_TARGET_P : $P_{DC} = I_{CONV}*(V_1-V_{RETURN})$

Then, we add this power to the power equations of the positive/negative and the return Nodes.

#### DC_NODE_TARGET_P : $\sum_{i} P_i + I_{CONV}*(V_1-V_{RETURN}) = 0$ for positive/negative

#### DC_NODE_TARGET_P : $\sum_{i} P_i - I_{CONV}*(V_1-V_{RETURN}) = 0$ for return

## - Grounding

One Node of the return layer must be connected to the ground, its potential is set to 0. So we remove its
power equation, and add an equation to set its potential :

#### DC_NODE_GROUND :  $V_{RETURN} = 0$