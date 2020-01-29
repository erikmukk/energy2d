package qlearning;

public class CustomPair<X, Y> {
    private X x;
    private Y y;

    public CustomPair(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public X getX() {
        return x;
    }

    public Y getY() {
        return y;
    }

    @Override
    public String toString() {
        return "(" + this.x.toString() + ", " + this.y.toString() + ")";
    }
}
