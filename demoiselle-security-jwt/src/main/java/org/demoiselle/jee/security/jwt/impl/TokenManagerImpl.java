/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.security.jwt.impl;

import org.demoiselle.jee.security.message.DemoiselleSecurityJWTMessages;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;
import java.util.List;
import java.util.Map;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import org.demoiselle.jee.core.api.security.DemoiselleUser;
import org.demoiselle.jee.core.api.security.Token;
import org.demoiselle.jee.core.api.security.TokenManager;
import org.demoiselle.jee.security.exception.DemoiselleSecurityException;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import static org.jose4j.jwt.NumericDate.fromMilliseconds;
import static org.jose4j.jwt.NumericDate.now;
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
 */
@ApplicationScoped
@Priority(AUTHENTICATION)
public class TokenManagerImpl implements TokenManager {

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @Inject
    private Logger logger;

    @Inject
    private Token token;

    @Inject
    private DemoiselleSecurityJWTConfig config;

    @Inject
    private DemoiselleUser loggedUser;

    @Inject
    private DemoiselleSecurityJWTMessages bundle;

    /**
     * Starts keys that are on file demoiselle-security-jwt.properties that
     * should be in the resource of your project
     */
    @PostConstruct
    public void init() {
        if (publicKey == null) {

            try {

                if (config.getType() == null) {
                    throw new DemoiselleSecurityException(bundle.chooseType(), 500);
                }

                if (!config.getType().equalsIgnoreCase(bundle.slave()) && !config.getType().equalsIgnoreCase(bundle.master())) {
                    throw new DemoiselleSecurityException(bundle.notType(), 500);
                }

                if (config.getType().equalsIgnoreCase(bundle.slave())) {
                    if (config.getPublicKey() == null || config.getPublicKey().isEmpty()) {
                        logger.warning(bundle.putKey());
                        throw new DemoiselleSecurityException(bundle.putKey(), 500);
                    } else {
                        publicKey = getPublic();
                    }
                }

                if (config.getType().equalsIgnoreCase(bundle.master())) {
                    privateKey = getPrivate();
                    publicKey = getPublic();
                }

            } catch (DemoiselleSecurityException | JoseException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                logger.severe(ex.getMessage());
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
        if (token.getKey() != null && !token.getKey().isEmpty()) {
            try {
                JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                        .setRequireExpirationTime()
                        .setAllowedClockSkewInSeconds(60)
                        .setExpectedIssuer(config.getRemetente())
                        .setExpectedAudience(config.getDestinatario())
                        .setEvaluationTime(now())
                        .setVerificationKey(publicKey)
                        .build();
                JwtClaims jwtClaims = jwtConsumer.processToClaims(token.getKey());
                loggedUser.setIdentity((String) jwtClaims.getClaimValue("identity"));
                loggedUser.setName((String) jwtClaims.getClaimValue("name"));
                loggedUser.setRoles((List) jwtClaims.getClaimValue("roles"));
                loggedUser.setPermissions((Map) jwtClaims.getClaimValue("permissions"));
                loggedUser.setParams((Map) jwtClaims.getClaimValue("params"));

                return loggedUser;
            } catch (InvalidJwtException ex) {
                loggedUser = null;
                token.setKey(null);
                logger.severe(ex.getMessage());
            }
        }
        return null;
    }

    @Override
    public void setUser(DemoiselleUser user) {
        long tempo = (long) (now().getValueInMillis() + (config.getTempo() * 60 * 1_000));
        try {
            JwtClaims claims = new JwtClaims();
            claims.setIssuer(config.getRemetente());
            claims.setExpirationTime(fromMilliseconds(tempo));
            claims.setAudience(config.getDestinatario());
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
            jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
            token.setKey(jws.getCompactSerialization());
            token.setType("JWT");
        } catch (JoseException ex) {
            logger.severe(ex.getMessage());
        }

    }

    @Override
    public boolean validate() {
        return getUser() != null;
    }

    private PrivateKey getPrivate() throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (config.getPrivateKey() == null) {
            KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
            keyGenerator.initialize(2_048);
            KeyPair kp = keyGenerator.genKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();
            config.setPrivateKey("-----BEGIN PRIVATE KEY-----" + getEncoder().encodeToString(privateKey.getEncoded()) + "-----END PRIVATE KEY-----");
            config.setPublicKey("-----BEGIN PUBLIC KEY-----" + getEncoder().encodeToString(publicKey.getEncoded()) + "-----END PUBLIC KEY-----");
            logger.log(WARNING, "privateKey={0}", config.getPrivateKey());
            logger.log(WARNING, "publicKey={0}", config.getPublicKey());
        }
        byte[] keyBytes = getDecoder().decode(config.getPrivateKey().replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
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
