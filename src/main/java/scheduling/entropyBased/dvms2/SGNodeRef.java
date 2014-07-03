package scheduling.entropyBased.dvms2;

public class SGNodeRef {

	String name;
    Long id;

    public SGNodeRef(String name, Long id){

		this.name = name; 
	}

	public String toString(){

		return this.name;
	}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public boolean isEqualTo(SGNodeRef otherRef) {

        return name.equals(otherRef.name);
    }

    public boolean isDifferentFrom(SGNodeRef otherRef) {

        return !isEqualTo(otherRef);
    }

    public boolean isSuperiorThan(SGNodeRef otherRef) {
        System.out.println("Please reimplement call to isSuperiorThan");
        return true;
    }
}
