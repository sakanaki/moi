package com.example.pentaho.component;

import org.springframework.stereotype.Component;

@Component
public class Login {

    /**
     * in reponse
     * for request header
     * to remain login status
     */
    private Token acessToken;

    /**
     * in reponse cookie
     * for cookie
     * to get acessToken if it's missing
     */
    private Token refreshToken;

    public Login() {
    }


    public Login(Token acessToken, Token refreshToken) {
        this.acessToken = acessToken;
        this.refreshToken = refreshToken;
    }

    public Token getAcessToken() {
        return acessToken;
    }

    public Token getRefreshToken() {
        return refreshToken;
    }

    /***
     * 成功登入後會有acessToken refreshToken
     * @param userId
     * @return
     */
    public static Login ofHS256(Long userId) {
        return new Login(Token.acessTokenofHS256(userId), Token.refreshTokenofHS256(userId));
    }


    /***
     * 成功登入後會有RASJWTToken
     * @param user
     * @return
     */
    public static Login ofRSAJWTToken(User user) {
        return new Login(Token.ofRSAJWT(user), Token.ofRSAJWT(user));
    }

    @Override
    public String toString() {
        return "Login{" +
                "acessToken=" + acessToken +
                ", refreshToken=" + refreshToken +
                '}';
    }
}