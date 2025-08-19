package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.ElementType;
public enum ConverterStationMode implements Quantity {
        RECTIFIER("rectifier", ElementType.BUS), //AC -> DC
        INVERTER("inverter", ElementType.BUS);//DC -> AC


        private final String symbol;

        private final ElementType elementType;

        ConverterStationMode(String symbol, ElementType elementType) {
            this.symbol = symbol;
            this.elementType = elementType;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }
    }


