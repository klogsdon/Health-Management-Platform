package EXT.DOMAIN.cpe.vpr;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import EXT.DOMAIN.cpe.vpr.pom.AbstractPOMObject;

import java.util.Map;

public class EncounterProvider extends AbstractPOMObject {
    private Long id;
    private Boolean primary;
    private String role;
    private String providerUid;
    private String providerName;
    private Encounter encounter;

    public EncounterProvider() {
        super(null);
    }

    @JsonCreator
    public EncounterProvider(Map<String, Object> vals) {
        super(vals);
    }

    public Long getId() {
        return id;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    public String getRole() {
        return role;
    }

    public String getProviderUid() {
        return providerUid;
    }

    public String getProviderName() {
        return providerName;
    }

    @JsonBackReference("encounter-provider")
    public Encounter getEncounter() {
        return encounter;
    }

    void setEncounter(Encounter encounter) {
        this.encounter = encounter;
    }
}
