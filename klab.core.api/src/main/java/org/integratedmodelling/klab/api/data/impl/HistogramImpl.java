package org.integratedmodelling.klab.api.data.impl;

import org.integratedmodelling.klab.api.data.Histogram;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class HistogramImpl implements Histogram {

    int[] bins;
    double[] boundaries;
    boolean empty = false;

    double min;

    double max;

    @Override
    public int[] getBins() {
        return bins;
    }

    public void setBins(int[] bins) {
        this.bins = bins;
    }

    @Override
    public double[] getBoundaries() {
        return boundaries;
    }

    public void setBoundaries(double[] boundaries) {
        this.boundaries = boundaries;
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    @Override
    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    @Override
    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }


    public Image image(int w, int h) {

        int divs = bins == null ? 0 : bins.length;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.createGraphics();
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (empty) {
            g.setColor(Color.RED);
            g.drawLine(0, 0, w - 1, h - 1);
            g.drawLine(0, h - 1, w - 1, 0);
        } else {
            int max = Arrays.stream(bins).max().getAsInt();
            int dw = w / divs;
            int dx = 0;
            g.setColor(Color.GRAY);
            for (int d : bins) {
                int dh = (int) ((double) h * (double) d / max);
                g.fillRect(dx, h - dh, dw, dh);
                dx += dw;
            }
        }
        return img;
    }
}
