package EXT.DOMAIN.cpe.team;

import EXT.DOMAIN.cpe.vpr.pom.AbstractPOMObject;

import java.util.Map;

public class Person extends AbstractPOMObject {

    private String name;

    public Person() {
        super(null);
    }

    public Person(Map<String, Object> vals) {
        super(vals);
    }

    public String getName() {
        return name;
    }
}
