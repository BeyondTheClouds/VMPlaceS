package configuration;

import java.util.Random;

public class ExtendedRandom extends Random {

	public ExtendedRandom() { super(); }
	public ExtendedRandom(long seed) {super(seed);}
	
	public double nextExp() {
		return - Math.log(1.0-nextDouble());
	}
	public double nextExp(double lambda) {
		return lambda*nextExp();
	}
	
	public double nextGamma(double shape) {
		
		if(shape == 1.0) {
			return nextExp();
		} else if (shape < 1.0) {
			double uniform, exp;
			while(true) {
				uniform = nextDouble();
				exp	= nextExp();
				if( uniform <= 1.0 - shape) {
					double res = Math.pow(uniform, 1.0/shape);
					if(res <= exp) return res;
				} else {
					double tmp = -Math.log((1-uniform) / shape);
					double res = Math.pow(1.0 + shape*(tmp - 1.0), 1.0/shape);
					if(res <= exp + tmp) return res;
				}
			}
		} else {
			double mshape = shape - (1.0/3.0);
			double coef = 1.0/Math.sqrt(9.0 * mshape);
			double g, tmp, uniform;
			while(true) {
				g = nextGaussian();
				tmp = 1.0 + coef * g;
				while (tmp <= 0.0) {
					g = nextGaussian();
					tmp = 1.0 + coef * g;
				}
				tmp = tmp * tmp * tmp; 
				uniform = nextDouble();
				if(uniform < 1.0 - 0.0331 * (g*g*g*g)) return (mshape * tmp);
				if(Math.log(uniform) < 0.5*g*g + mshape*(1.0 - tmp + Math.log(tmp))) return (mshape * tmp);
			}
		}
	}
	public double nextGamma(double shape, double scale) {
		return scale * nextGamma(shape);
	}
	
	public double nextBeta(double alpha, double beta) {
		if((alpha <= 1.0) && (beta <= 1.0)) {
			double p1, p2;
			while(true) {
				p1 = Math.pow(nextDouble(), 1.0/alpha);
				p2 = Math.pow(nextDouble(), 1.0/beta);
				if((p1 + p2) <= 1.0) 
					return (p1/(p1+p2));
			}
		} else {
			double g1 = nextGamma(alpha);
			double g2 = nextGamma(beta);
			return (g1/(g1+g2));
		}
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
}
