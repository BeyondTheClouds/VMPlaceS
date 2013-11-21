package configuration;

public class BetaDistribution {

	ExtendedRandom r;
	double alpha, beta;
	
	private void init(ExtendedRandom r, double mean, double var)throws Exception {
		this.r = r;
		if(var >= mean * (1-mean)) 
			throw new Exception("BetaDistribution : incorrect parameters : " + mean + " " + var);
		double nu = mean*(1.0 - mean)/var - 1.0;
		this.alpha = mean * nu;
		this.beta = (1.0 - mean) * nu;
	}
	public BetaDistribution(ExtendedRandom r, double mean, double var) throws Exception {
		init(r, mean, var);
	}
/*	public BetaDistribution(double mean, double var) throws Exception {
		init(new ExtendedRandom(), mean, var);
	}*/
	public BetaDistribution(double alpha, double beta) throws Exception {
		this.r = new ExtendedRandom();
        this.alpha = alpha ;
        this.beta = beta ;
	}

	public double nextValue() {
		return r.nextBeta(alpha, beta);
	}
	
    public static void main (String args[]){
        int[] tab=new int[101];
        BetaDistribution bd = null;

        for (int i=0 ; i <101 ; i++){
            tab[i]=0;
        }

        try {
            bd = new BetaDistribution(3,1);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        for (int i=0 ; i < 100000 ; i ++)
             tab[(int)(bd.nextValue()*100)]++;

        for (int i=0 ; i <101 ; i++)
        System.out.println(tab[i]);
    }

}
