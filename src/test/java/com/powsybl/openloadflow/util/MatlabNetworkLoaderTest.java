package com.powsybl.openloadflow.util;
import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.iidm.network.*;
import com.powsybl.matpower.converter.MatpowerImporter;

import java.nio.file.Path;
import java.util.Properties;

public class MatlabNetworkLoaderTest {
    public static void main(String[] args) {
        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case6515rte"),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case6515rte"));
//        Network network = Network.read("C:/Users/jarchambault/Downloads/case6515rte.mat");

    }
}