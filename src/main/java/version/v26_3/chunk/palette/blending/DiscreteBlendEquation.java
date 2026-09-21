package version.v26_3.chunk.palette.blending;

public class DiscreteBlendEquation implements IBlendEquation {
    double[] steps;

    public DiscreteBlendEquation(double... steps) {
        this.steps = steps;
    }

    @Override
    public double getRatio(int depth) {
        if (depth >= steps.length) { return 1.0f; }

        return steps[depth];
    }
}
