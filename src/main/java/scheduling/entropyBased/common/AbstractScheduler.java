package scheduling.entropyBased.common;

import java.util.Random;

/**
 * Classe représentant un scheduler Abstrait s'appuyant d'Entropy
 * Basée sur l'ancienne classe entropy2.AbstractScheduler
 */
public abstract class AbstractScheduler implements EntropyBasedScheduler {



    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Instance variables
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The time spent to compute VMPP
     *  @deprecated Please consider that this value is currently deprecated and will be set to zero untill it will be fixed - Adrien, Nov 18 2011
     */
    protected long timeToComputeVMPP;

    //The time spent to compute VMRP
    protected long timeToComputeVMRP;

    //The time spent to apply the reconfiguration plan
    protected long timeToApplyReconfigurationPlan;

    //The cost of the reconfiguration plan
    protected int reconfigurationPlanCost;

    //The number of migrations inside the reconfiguration plan
    protected int nbMigrations;

    //The depth of the graph of the reconfiguration actions
    protected int reconfigurationGraphDepth;

    //Adrien, just a hack to serialize configuration and reconfiguration into a particular file name
    protected int loopID;


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractScheduler() {
        this.timeToComputeVMPP = 0;
        this.timeToComputeVMRP = 0;
        this.timeToApplyReconfigurationPlan = 0;
        this.reconfigurationPlanCost = 0;
        this.nbMigrations = 0;
        this.reconfigurationGraphDepth = 0;
        this.loopID = new Random().nextInt();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Accessors
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public int getReconfigurationPlanCost() {
        return reconfigurationPlanCost;
    }

    /**
     *  @deprecated Please consider that this value is currently deprecated and will be set to zero untill it will be fixed - Adrien, Nov 18 2011
     */
    public long getTimeToComputeVMPP() {
        return timeToComputeVMPP;
    }

    public long getTimeToComputeVMRP() {
        return timeToComputeVMRP;
    }

    public long getTimeToApplyReconfigurationPlan() {
        return timeToApplyReconfigurationPlan;
    }

    public int getNbMigrations(){
        return nbMigrations;
    }

    public int getReconfigurationGraphDepth(){
        return reconfigurationGraphDepth;
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Abstract methods
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public abstract ComputingState computeReconfigurationPlan();

    /**
     * @return 0 if the reconfiguration plan has been correctly performed (i.e. completely)
     */
    public abstract void applyReconfigurationPlan();

}
