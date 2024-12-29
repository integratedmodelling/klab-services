package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class DataBuilderImpl implements Data.Builder {

    @Override
    public Data.FillCurve fillCurve() {
        return null;
    }

    @Override
    public Data.BooleanFiller booleanState(Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.BooleanFiller booleanState(String stateIdentifier, Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.FloatFiller floatState(Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.FloatFiller floatState(String stateIdentifier, Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.IntFiller intState(Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.IntFiller intState(String stateIdentifier, Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.DoubleFiller doubleState(Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.DoubleFiller doubleState(String stateIdentifier, Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.KeyedFiller keyedState(Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.KeyedFiller keyedState(String stateIdentifier, Data.FillCurve fillCurve) {
        return null;
    }

    @Override
    public Data.ObjectFiller objectCollection() {
        return null;
    }

    @Override
    public Data.ObjectFiller objectCollection(String observationIdentifier) {
        return null;
    }

    @Override
    public void notification(Notification notification) {

    }

    @Override
    public Data build() {
        return null;
    }
}
