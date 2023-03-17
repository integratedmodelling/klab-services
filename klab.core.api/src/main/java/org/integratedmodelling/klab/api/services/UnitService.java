package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Concept;

public interface UnitService extends KlabService {

    Unit getDefaultUnitFor(Concept concept);

    Unit getUnit(String string);

}
