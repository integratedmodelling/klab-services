package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.utils.Parameters;

public class GridImpl implements Grid {

    private static final long serialVersionUID = -4637331840972669199L;

    private ProjectionImpl projection;
    private long X, Y;
    private EnvelopeImpl envelope;
    private double xSize, ySize;

	public static Grid create(double resolutionInM) {
		// TODO Auto-generated method stub
		return null;
	}

    public GridImpl() {

    }
    
    public GridImpl(Parameters<String> definition) {

    }

    @Override
    public int getXCells() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getYCells() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Envelope getEnvelope() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long size() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getXCellSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getYCellSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Projection getProjection() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Grid align(Grid other) {
        // TODO Auto-generated method stub
        return null;
    }


}
