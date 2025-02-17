# LfNetwork post-processors

Any simulation performed by PowSyBl Open LoadFlow starts by loading the 
[iIDM Grid Model](inv:powsyblcore:*:*:#grid_model) into PowSyBl Open LoadFlow representation: the `LfNetwork`
[![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-open-loadflow/latest/com/powsybl/openloadflow/network/LfNetwork.html).

By providing an `LfNetworkLoaderPostProcessor` [![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-open-loadflow/latest/com/powsybl/openloadflow/network/LfNetworkLoaderPostProcessor.html)
plug-in, it is possible to further alter the `LfNetwork`. The plug-in is called by PowSyBl Open LoadFlow network loader:
- at each bus, branch, injection, and area creation
- and when the `LfNetwork` loading is complete

In each callback a reference to the original iIDM object is provided, as well as the corresponding object in `LfNetwork`.

Below an example empty post-processor, doing nothing:

```java
package org.example.powsybl.plugins;

import com.google.auto.service.AutoService;
import com.powsybl.openloadflow.network.*;

@AutoService(LfNetworkLoaderPostProcessor.class)
public class MyLfNetworkLoaderPostProcessor implements LfNetworkLoaderPostProcessor {

    @Override
    public String getName() {
        return "my-example-plugin";
    }

    @Override
    public LoadingPolicy getLoadingPolicy() {
        return LoadingPolicy.ALWAYS;
    }

    @Override
    public void onBusAdded(Object element, LfBus lfBus) {
        // implement me
    }

    @Override
    public void onBranchAdded(Object element, LfBranch lfBranch) {
        // implement me
    }

    @Override
    public void onInjectionAdded(Object element, LfBus lfBus) {
        // implement me
    }

    @Override
    public void onAreaAdded(Object element, LfArea lfArea) {
        // implement me
    }

    @Override
    public void onLfNetworkLoaded(Object element, LfNetwork lfNetwork) {
        // implement me
    }
}
```

For example, if you have a need to copy some extra data from iIDM elements to LfNetwork elements `PropertyBag`
[![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-open-loadflow/latest/com/powsybl/openloadflow/network/PropertyBag.html),
you may proceed as follows:

```java
    @Override
    public void onBranchAdded(Object element, LfBranch lfBranch) {
        if (element instanceof Identifiable<?> identifiable) {
            lfBranch.setProperty("name-or-id", identifiable.getNameOrId());
        }
    }
```

The above example stores the iIDM branch name in the LfBranch as a property having key `name-or-id`.

Note that `PropertyBag`-s are not limited to storing strings: you can attach a complex object behind a property.
One practical use case of doing so is to attach to the LfNetwork specific information needed to run custom Outer-Loops.
Refer to [Outer-Loop Configuration](outerloop_configuration.md).

Note that besides attaching properties, manipulating the created LfNetwork elements attributes directly is
another perfectly valid use case for the `LfNetworkLoaderPostProcessor`.
Just remember: with great power comes great responsibilities.
