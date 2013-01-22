package EXT.DOMAIN.cpe.vpr.ws.json;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Jackson mix-in to add json serialization annotations to the {@link EXT.DOMAIN.cpe.vista.springframework.security.userdetails.VistaUserDetails} interface.
 *
 * @see "http://wiki.fasterxml.com/JacksonMixInAnnotations"
 */
public interface VistaUserDetailsJacksonAnnotations extends UserDetailsJacksonAnnotations {
    @JsonIgnore
    String getAccessCode();
    @JsonIgnore
    String getVerifyCode();
}
