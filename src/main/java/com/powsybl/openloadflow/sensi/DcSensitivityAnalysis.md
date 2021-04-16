The sensitivity analysis in DC mode contains multiples small performances optimization, making the code quite hard to understand.

Here, we will try to detail the inner workings of the **analyse** method to make it more understandable.

We may sometimes refer to [the documentation about the DC sensitivity analysis](https://www.powsybl.org/pages/documentation/simulation/sensitivity/openlf.html), so I strongly advise getting familiar with it first. This page will be called "the documentation" in the rest of this file.

## Preparing the sensitivity computation
The beginning of the function is quite similar to AC sensitivity analysis. We load our Network into a LfNetwork, then we check the validity of every contingency, the distribution parameters, and we create the factors. 

Then, we make an additional check to make sure that all the factors that we are trying to compute are supported in DC (for example, a Voltage sensitivity wouldn't make sense, and we would raise an Exception).
Once we are sure that all the factors are valid factors, we aggregate them into groups, were each group correspond to the same variable (so the state of the network will be the same for all factors within a same group). There may be some additional grouping done. For example, injections on different generator of the same bus will be grouped in the same group.

The first thing we do is to compute the distribution of the slack on the base network, and create a map that maps each bus to the opposite of it's participation factor. This will be used later to compensate the injection factors.

As you can read in the documentation (linked at the beginning of this document), in DC mode, the contingencies are not managed by deactivating some equations, but by virtually removing the corresponding branches by injecting a given amount of MW on each side of the branch, and computing the corresponding sensitivity. To do so, we have created a new class of object, **ComputedContingencyElement**, that will contain some indexes, in order allow us to extract some data from a matrix, and compute this amount of MW to inject in ordre to virtually remove the branch. The next step in the code is then to create those object, by iterating over all the branches from all contingencies (without including Hvdc branches, because they are not treated as branches in our LfNetwork).

After creating the jacobian matrix, we run a loadflow to compute the references of the functions of the sensitivity factors, and we benefit from this computation to extract the states matrix after the loadflow (a matrix containing the angles for every bus).

The next step is to compute the states for the sensitivities. We fill a right-hand-side matrix, following the given convention:
* For an injection on a bus, we write 1 on the line corresponding to this bus (more precisely, the line corresponding to the **BUS_P** equation of this bus), and then 0 for all the other lines. Then, we substract to each line the participation of the corresponding bus, to compensate the injection. For example, if you have a 3-bus network, with a generator on each bus, with the following compensation factors:
  * Bus 1: 0.5
  * Bus 2: 0.1
  * Bus 3: 0.4

  Then the column corresponding to an injection on bus 1 in the right hand side will look like this:
  $$$ \begin{pmatrix}
1-0.5\\
-0.1\\
-0.4
\end{pmatrix} $$$
* For a sensitivity on a phase tap changer, then the equation system must contain an equation in **ALPHA_1** corresponding to the phase tap changer (this is enforced by passing a forceA1var to create the equation system), and we just write 1 on the line corresponding to this equation.

We define the same type of matrix to represent the contingencies, by writing +1 on one side of the branch, and -1 on the other.

Those two matrix being defined, we solve the associated system with the jacobian. This gives us the states of the network corresponding to each modification (a factor, or an injection on the sides of a contingency). From the factor states, we can extract the sensitivity value for each factor, on the *base network* (without any contingency). We store this base sensitivity in each factor. Then, we use it to create some results for the *base network*.

Once this is done, all that's left is to compute the sensitivities for each contingency. To do so, we need to distinguish several cases for each contingency:
  * Whether or not we lost the connectivity of the network when applying a contingency (because then, the slack distribution will change).
  * Whether or not we applied the contingency on a transformer. (we will need to deactivate the **ALPHA_1** equation, and recompute the loadflow to obtain the function reference).
  * Whether or not we applied the contingency on an HVDC line (because then, then slack distribution may also change).

The next natural step is then to divide contingencies in two categories: 
  * The contingencies that do not trigger a loss of connectivity
  * The contingencies that does trigger a loss of connectivity (and we will index them by their branches that are responsible for the loss of connectivity - meaning each side of the branch is in a different connected component of the network-)

### Detecting the loss of connectivity

In fact, we will start this division by making an approximation (function *detectPotentialConnectivityLoss*). For each contingency, we iterate through the branches of the contingency, and look at how making +1-1 on this branch impacts the other branches. If the sum of the flow passing on the other branches is less than one, then we are sure that the connectivity has not been lost. Otherwise, we may have lost the connectivity, and the elements responsible for the loss of connectivity are included in the set of elements that have a non-zero sensitivity.  
The good thing about this is that it's pretty fast, because the states for the +1-1 on the branches have already been computed in the previous steps.

It is now time to refine the results a bit, and run a connectivity analysis on the contingencies that were *potentially* breaking connectivity, to know precisely which ones break the connectivitity, and which ones don't. This is also where we index the results by the branches losing connectivity. To do so, we iterate through every group of element "potentially breaking connectivity". We cut their branches, and we check for each element whether its two sides are in different connected component or not. If this is the case, then this element is (partially) responsible for the loss of connectivity.

## Computing sensitivities without loss of contingency

We will now start processing the values for contingencies where there is no loss of connectivity.  Again, we need to distinguish between two cases: Was the contingency applied on a transformer or not ? This question is simply answered by looking at the list of lost branches, and checking if there is a transformer. 

We will start by processing the contingencies that does not include a transformer. But again, two cases must be managed: Did we lost an HvdcLine in the contingency ? Again, this is done by checking if there is any HvdcLine in the contingency elements.

If not, then this is very simple, we can just compute the sensitivities by following the equations written in the documentation.

### Computing sensitivities for a basic contingency
The equations of the documentation show that the sensitivity with respect to a given contingency can be obtained from the sensitivity on the *base network* (computed previously), and from the virtual cancelling of the branches contained in the contingency. To cancel the branch, we need to have the states for making +1-1 on each branch (we have computed this previously). We then need to compute what is called *alpha* in the equation of the documentation, for each element (branch) of the contingency. The value of the *alpha* will be different for the function reference and the sensitivities. We store this alpha inside the object of the **ComputedContingencyElement** that we mentionned earlier. Once *alpha* is computed, we can apply the formula given in the documentation to get the function reference and the sentitivity value with respect to the contingency. (sensitivity = base sensitivity + sum_over_elements(alpha_element * sensitivity of +1-1 on the element) ).

### Computing the sentivities if we lost an HVDC
If we lost a contingency, we need to apply some modifications to our network and recompute the loadflow. The modifications consists in setting the generator targetP to 0 if the converter station was a Vsc, or substracting some value from the buses' loadTargetP if the converter station was a Lcc. Then if the slack was distributed, and the balance type was relative to the load, we need to recompute the distribution of the slack, and thus the right-hand-side matrix. Once this is done, we compute the new loadflow (after modifications), and we can compute the new contingency values, as we have done in [Computing sensitivities for a basic contingency](#computing-sensitivities-for-a-basic-contingency).  
Be careful, as we have modified some values of the network when updating the Lcc and Vsc, we need to restore the network as it was before, so we do not impact the next computations.

Then we can iterate through the contingencies for which we did not lost the connectivity, but we lost a transformer.

### Computing the sensitivities if a transformer has been lost
If some transformers have been lost, we need to deactivate all the **ALPHA_1** equations and recompute the loadflow. This is done by passing a of *disabledBranches* to the *setReferenceActivePowerFlows* method. This set of disabledBranches has been computed when calling the **PhaseTapChangerContingenciesIndexing** class, and our contingencies have been indexed by set of transformers, to optimize the performances and compute as few loadflows as possible.  
You may notice that we override the *flowStates* variable, but this does not matter, because the previous value of this variable was only useful for contingencies for which we did not loose the connectivity, and no transformers (and no HVDC).  
Then, once we have our new loadflow, we can compute the contingencies, distinguishing between a loss of HVDC and no loss of HVDC, as we have done previously. Note that the loadflow that we just ran will be used for every contingency without HVDC, but will be computed again for every contingency with an HVDC.

It is now time to compute the sensitivities value for contingencies that breaks the connectivity of the network.

## Computing the sensitivities if connectivity has been broken
Just as we have done to optimize the number of loadflows computed in case of a loss of transformer, we have done some indexing to optimize this. Some branches are *responsible* for the loss of connectivity. Meaning, their two sides are in different connected components. Different contingencies may have the same elements resposible for the loss of connectivity, so we would need to do some computation twice. For example, if losing a branch *l1* breaks the connectivity, but losing branches *l2*, *l3* and *l4* does not affect connectivity, then the contingencies {*l1*, *l2*}, {*l1*, *l3*} and {*l1*, *l4*} will have the same connectivity, the same distribution of slack, and the same GLSKs, and we do not need to compute those multiple times.  
So, we have a set of element responsible for the loss of connectivity, and a list of contingency containing these elements. The first thing we do is to check for predefined results. For exemple, the function and the variable of a factor may be in different connected components, in which case, the sensititivity will obviously be **0**. Maybe the function and the variable are in a connected component which is not the one containing our slack bus. In this case, we do not know what happens in this connected component, and we return **NaN**.

So, we lost connectivity, and we may have lost some buses that were part of the slack distribution. This is the first thing we check. In the buses that we lost (i.e. that are not part of the main connected component anymore), were some of them part of the distribution ? If yes, then we will need to recompute the slack distribution, change the right hand side, and compute new states for the factors. So, we check if we need to do so, and store the result in the boolean **rhsChanged**.  
The same thing goes for glsk (and all multi variables). If we lost some generators that were part of the GLSK, we need to recompute the new distribution of the glsk (the total injection must always be 1), and recompute the right hand side. Again, we store the result in **rhsChanged**.

If one of the two previous condition is met, we recompute the factor states. We compute the new slack distribution, and we create a new matrix to hold the new states, and use it to compute some new "base sensitivities". These base case sentivity values do not represent the value on the base network anymore, but the value of the sensitivities in the main connected component.

Then, according to the section **Loss of connectivity by more than one branch** of the documentation, we need to find a set of branches that reconnects all the connected components without creating any cycle between them. For performances' sake, this was done at the same time than computing the branches responsible for the loss of connectivity.

We also need to recompute the new flow states, and we do this by passing the list of disabled buses.

Now, we have brand new flow states, brand new factor states, brand new base case sensitivities and a list of branches to reconnect. We can now compute the sensitivities, exactly as we have done for the sensitivities. The only difference is that the branches that will be reconnected should not be taken into account when using the formula of the documentation (when using the +1-1 sensitivities), so we filter them out.

Once we have computed all the contingencies for this connectivity, we need to reset the base case sensitivities if we changed them, because some next iterations may use it. For example, some contingencies may change the distribution, and thus will use the base case sensitivity.