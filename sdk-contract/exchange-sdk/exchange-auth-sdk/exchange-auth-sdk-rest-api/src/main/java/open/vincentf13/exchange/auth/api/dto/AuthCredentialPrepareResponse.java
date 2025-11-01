package open.vincentf13.exchange.auth.api.dto;

public record AuthCredentialPrepareResponse(
        String secretHash,
        String salt
) { }
