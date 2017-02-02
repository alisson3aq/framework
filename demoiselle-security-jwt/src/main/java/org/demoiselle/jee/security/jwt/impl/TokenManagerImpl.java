/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.security.jwt.impl;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import static javax.ws.rs.Priorities.AUTHORIZATION;
import javax.ws.rs.core.Response;

import org.demoiselle.jee.core.api.security.DemoiselleUser;
import org.demoiselle.jee.core.api.security.Token;
import org.demoiselle.jee.core.api.security.TokenManager;
import org.demoiselle.jee.core.api.security.TokenType;
import org.demoiselle.jee.security.exception.DemoiselleSecurityException;
import org.demoiselle.jee.security.message.DemoiselleSecurityJWTMessages;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.RsaKeyUtil;
import org.jose4j.lang.JoseException;

/**
 * The security risk component JWT use a strategy where a pair of asymmetric
 * keys is necessary. The keys can be generated by you on linux
 * https://help.ubuntu.com/community/SSH/OpenSSH/Keys or leave the parameters
 * blank and see the log suggested keys The JWT standard is one of the safest
 * and performers today, to learn more check https://jwt.io/
 *
 * @author SERPRO
 */
@RequestScoped
@Priority(AUTHORIZATION)
public class TokenManagerImpl implements TokenManager {

    private static PublicKey publicKey;
    private static PrivateKey privateKey;

    private static final Logger logger = Logger.getLogger(TokenManagerImpl.class.getName());

    @Inject
    private Token token;

    @Inject
    private DemoiselleSecurityJWTConfig config;

    @Inject
    private DemoiselleUser loggedUser;

    @Inject
    private DemoiselleSecurityJWTMessages bundle;

    /**
     * Starts keys that are on file demoiselle.properties that should be in the
     * resource of your project
     */
    @PostConstruct
    public void init() {
        if (publicKey == null) {

            try {

                if (config.getType() == null) {
                    throw new DemoiselleSecurityException(bundle.chooseType(), Response.Status.UNAUTHORIZED.getStatusCode());
                }

                if (!config.getType().equalsIgnoreCase(bundle.slave()) && !config.getType().equalsIgnoreCase(bundle.master())) {
                    throw new DemoiselleSecurityException(bundle.notType(), Response.Status.UNAUTHORIZED.getStatusCode());
                }

                if (config.getType().equalsIgnoreCase(bundle.slave())) {
                    if (config.getPublicKey() == null || config.getPublicKey().isEmpty()) {
                        throw new DemoiselleSecurityException(bundle.putKey(), Response.Status.UNAUTHORIZED.getStatusCode());
                    } else {
                        publicKey = getPublic();
                    }
                }

                if (config.getType().equalsIgnoreCase(bundle.master())) {
                    privateKey = getPrivate();
                    publicKey = getPublic();
                }

            } catch (JoseException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new DemoiselleSecurityException(bundle.general(), Response.Status.UNAUTHORIZED.getStatusCode(), ex);
            }

        }
    }

    /**
     * Pick up the token that is in the request scope and draws the user into
     * the token validating the user at this time
     *
     * @return DemoiselleUser principal
     */
    @Override
    public DemoiselleUser getUser() {
        return getUser(null, null);
    }

    /**
     * Pick up the token that is in the request scope and draws the user into
     * the token validating the user at this time
     *
     * @return DemoiselleUser principal
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DemoiselleUser getUser(String issuer, String audience) {
        if (!token.getType().equals(TokenType.JWT)) {
            throw new DemoiselleSecurityException(bundle.notJwt(), Response.Status.BAD_REQUEST.getStatusCode());
        }
        if (token.getKey() != null && !token.getKey().isEmpty()) {
            try {
                JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                        .setRequireExpirationTime()
                        .setAllowedClockSkewInSeconds(60)
                        .setExpectedIssuer(issuer != null ? issuer : config.getIssuer())
                        .setExpectedAudience(audience != null ? audience : config.getAudience())
                        .setEvaluationTime(org.jose4j.jwt.NumericDate.now())
                        .setVerificationKey(publicKey)
                        .build();
                JwtClaims jwtClaims = jwtConsumer.processToClaims(token.getKey());
                loggedUser.setIdentity((String) jwtClaims.getClaimValue("identity"));
                loggedUser.setName((String) jwtClaims.getClaimValue("name"));
                List<String> list = (List<String>) jwtClaims.getClaimValue("roles");
                list.stream().forEach((string) -> {
                    loggedUser.addRole(string);
                });

                Map<String, List<String>> mappermissions = (Map) jwtClaims.getClaimValue("permissions");
                mappermissions.entrySet().stream().forEach((entry) -> {
                    String key = entry.getKey();
                    List<String> value = entry.getValue();
                    value.forEach((string) -> {
                        loggedUser.addPermission(key, string);
                    });
                });

                Map<String, String> mapparams = (Map) jwtClaims.getClaimValue("params");
                mapparams.entrySet().stream().forEach((entry) -> {
                    loggedUser.addParam(entry.getKey(), entry.getValue());
                });
                return loggedUser;
            } catch (InvalidJwtException ex) {
                loggedUser = null;
                token.setKey(null);
                throw new DemoiselleSecurityException(bundle.expired(), Response.Status.UNAUTHORIZED.getStatusCode(), ex);
            }
        }
        return null;
    }

    @Override
    public void setUser(DemoiselleUser user) {
        setUser(user, null, null);
    }

    @Override
    public void setUser(DemoiselleUser user, String issuer, String audience) {
        long time = (org.jose4j.jwt.NumericDate.now().getValueInMillis() + (config.getTimetoLiveMilliseconds()));
        try {
            JwtClaims claims = new JwtClaims();
            claims.setIssuer(issuer != null ? issuer : config.getIssuer());
            claims.setExpirationTime(org.jose4j.jwt.NumericDate.fromMilliseconds(time));
            claims.setAudience(audience != null ? audience : config.getAudience());
            claims.setGeneratedJwtId();
            claims.setIssuedAtToNow();
            claims.setNotBeforeMinutesInThePast(1);

            claims.setClaim("identity", (user.getIdentity()));
            claims.setClaim("name", (user.getName()));
            claims.setClaim("roles", (user.getRoles()));
            claims.setClaim("permissions", (user.getPermissions()));
            claims.setClaim("params", (user.getParams()));

            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(claims.toJson());
            jws.setKey(privateKey);
            jws.setKeyIdHeaderValue("demoiselle-security-jwt");
            jws.setAlgorithmHeaderValue(config.getAlgorithmIdentifiers());
            token.setKey(jws.getCompactSerialization());
            token.setType(TokenType.JWT);
        } catch (JoseException ex) {
            throw new DemoiselleSecurityException(bundle.general(), Response.Status.UNAUTHORIZED.getStatusCode(), ex);
        }

    }

    @Override
    public boolean validate() {
        return getUser() != null;
    }

    @Override
    public boolean validate(String issuer, String audience) {
        return getUser(issuer, audience) != null;
    }

    /**
     *
     * This method creates public and private keys, RSA 2048bits. When it is not
     * found in demoiselle.properties the pair is created and written in the
     * server log to be copied and placed in demoiselle.properties
     *
     * @return Private key
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private PrivateKey getPrivate() throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (config.getPrivateKey() == null) {
            KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
            keyGenerator.initialize(2_048);
            KeyPair kp = keyGenerator.genKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();
            logger.warning("privateKey=" + publicKey.toString());
            logger.warning("publicKey=" + privateKey.toString());
            throw new DemoiselleSecurityException(bundle.putKey(), Response.Status.UNAUTHORIZED.getStatusCode());
        }
        byte[] keyBytes = java.util.Base64.getDecoder().decode(config.getPrivateKey().replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey getPublic() throws JoseException, InvalidKeySpecException {
        RsaKeyUtil rsaKeyUtil = new RsaKeyUtil();
        return rsaKeyUtil.fromPemEncoded(config.getPublicKey());
    }

    @Override
    public void removeUser(DemoiselleUser user) {
        throw new UnsupportedOperationException(bundle.notJwt());
    }

}
