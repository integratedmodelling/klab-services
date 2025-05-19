package org.integratedmodelling.klab.api.data.impl;

import org.integratedmodelling.klab.api.data.Histogram;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class HistogramImpl implements Histogram {

    public static class BinImpl implements Bin {

        private double mean;
        private double min;
        private double max;
        private double count;
        private double sum;
        private double sumSquared;
        private String category;
        private double weight;
        double missingCount;

        @Override
        public double getMean() {
            return this.mean;
        }

        @Override
        public double getMin() {
            return this.min;
        }

        @Override
        public double getMax() {
            return this.max;
        }

        @Override
        public double getCount() {
            return this.count;
        }

        @Override
        public double getSum() {
            return this.sum;
        }

        @Override
        public double getSumSquared() {
            return this.sumSquared;
        }

        @Override
        public String getCategory() {
            return this.category;
        }

        public void setMean(double mean) {
            this.mean = mean;
        }

        public void setMin(double min) {
            this.min = min;
        }

        public void setMax(double max) {
            this.max = max;
        }

        public void setCount(double count) {
            this.count = count;
        }

        public void setSum(double sum) {
            this.sum = sum;
        }

        @Override
        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public void setSumSquared(double sumSquared) {
            this.sumSquared = sumSquared;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        @Override
        public double getMissingCount() {
            return missingCount;
        }

        public void setMissingCount(double missingCount) {
            this.missingCount = missingCount;
        }
    }

    List<Bin> bins = new ArrayList<>();
    boolean empty = false;
    double missingCount;
    double min;
    double max;

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

    @Override
    public List<Bin> getBins() {
        return this.bins;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public void setBins(List<Bin> bins) {
        this.bins = bins;
    }

    @Override
    public double getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(double missingCount) {
        this.missingCount = missingCount;
    }

    public Image image(int w, int h) {

        int divs = bins == null ? 0 : bins.size();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
//        img.createGraphics();
//        Graphics2D g = (Graphics2D) img.getGraphics();
//        g.setColor(Color.WHITE);
//        g.fillRect(0, 0, w, h);
//        if (empty) {
//            g.setColor(Color.RED);
//            g.drawLine(0, 0, w - 1, h - 1);
//            g.drawLine(0, h - 1, w - 1, 0);
//        } else {
//            int max = Arrays.stream(bins).max().getAsInt();
//            int dw = w / divs;
//            int dx = 0;
//            g.setColor(Color.GRAY);
//            for (int d : bins) {
//                int dh = (int) ((double) h * (double) d / max);
//                g.fillRect(dx, h - dh, dw, dh);
//                dx += dw;
//            }
//        }
        return img;
    }
}
