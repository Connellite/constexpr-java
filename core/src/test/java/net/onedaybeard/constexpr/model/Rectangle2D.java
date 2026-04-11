package net.onedaybeard.constexpr.model;

public class Rectangle2D {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public Rectangle2D(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return "Rectangle2D [x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
}