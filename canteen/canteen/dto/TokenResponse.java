package vn.edu.phenikaa.canteen.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, AuthUser user) {

    public static TokenResponse of(String access, String refresh, long expiresInSeconds, AuthUser user) {
        return new TokenResponse(access, refresh, "Bearer", expiresInSeconds, user);
    }
}
