package com.example.pentaho.service;

import com.example.pentaho.component.*;
import com.example.pentaho.utils.RSAJWTUtils;
import com.example.pentaho.utils.RsaUtils;
import com.example.pentaho.utils.UserContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.Map;

@Service
public class ApiKeyService {
    private static Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    @Autowired
    private KeyComponent keyComponent;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private final int VALID_TIME = 1440;

    public JwtReponse getApiKey(String userId ) throws Exception {
        User user = UserContextUtils.getUserHolder();
        log.info("user:{}", user);
        PrivateKey privateKey = RsaUtils.getPrivateKey((keyComponent.getApPrikeyName()));
        //一般token
        Map<String, Object> toeknMap = RSAJWTUtils.generateTokenExpireInMinutes(user, privateKey, VALID_TIME); //一般TOKEN效期先設一天
        //refresh_token
        Map<String, Object> refreshTokenMap = RSAJWTUtils.generateTokenExpireInMinutes(user, privateKey, VALID_TIME * 2);  //REFRESH_TOKEN效期先設2天
        String refreshToken = (String) refreshTokenMap.get("token");
        Token token = new Token((String) toeknMap.get("token"), (String) toeknMap.get("expiryDate"));
        //refresh_token存table
        refreshTokenService.saveRefreshToken(user == null ? userId : user.getId(), refreshToken);
        //設定返回給前端的物件
        JwtReponse jwtReponse = new JwtReponse();
        jwtReponse.setRefreshToken((String) refreshTokenMap.get("token"));
        jwtReponse.setRefreshTokenExpiryDate((String) refreshTokenMap.get("expiryDate"));
        jwtReponse.setToken(token.getToken());
        jwtReponse.setExpiryDate(token.getExpiryDate());
        return jwtReponse;
    }
}
