/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.yofc.opcuaserver;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yofc.opcuaserver.configuration.Configuration;
import com.yofc.opcuaserver.configuration.PasswordConfiguration;
import com.yofc.opcuaserver.context.CertificateGenerator;
import com.yofc.opcuaserver.context.CertificateKeyPair;
import com.yofcopcuaserver.backend.Plc4xNamespace;

public class OPCUAServer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Configuration config;
    private PasswordConfiguration passwordConfig;

    private final OpcUaServer server;
    private final Plc4xNamespace plc4xNamespace;
    private final String certificateFileName = "opcuaserver.pfx";

    private void setPasswordWrapper() {
        try {
            passwordConfig.setSecurityPassword(config.getSecurityPassword());
            passwordConfig.createUser(config.getUsername(), config.getPassword(), config.getGroup());
        } catch (IOException e) {
            throw new RuntimeException("Unable to save config file, please check folder permissions. ", e);
        }
    }

    private void readPasswordConfig() {
        // Read Config File
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try {
            Path path = FileSystems.getDefault().getPath(config.getDir()).resolve("security/.jibberish");
            File file = path.toFile();
            boolean resetPassword = config.getResetPassword() == null ? false : config.getResetPassword();
            if (file.isFile() && !resetPassword) {
                passwordConfig = mapper.readValue(file, PasswordConfiguration.class);
                passwordConfig.setPasswordConfigFile(path);
            } else if (file.isFile() && resetPassword) {
                passwordConfig = mapper.readValue(file, PasswordConfiguration.class);
                passwordConfig.setPasswordConfigFile(path);
                setPasswordWrapper();
            } else {
                file.getParentFile().mkdirs();
                passwordConfig = new PasswordConfiguration();
                passwordConfig.setVersion(config.getVersion());
                passwordConfig.setPasswordConfigFile(path);
                setPasswordWrapper();
            }
        } catch (IOException e) {
            logger.info("Error parsing password file ", e);
        }
    }

    private void readConfigFile(String configFile) {
        // Read Config File
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try {
            config = mapper.readValue(new File(configFile), Configuration.class);
            config.setConfigFile(configFile);
            // Checking if the security directory has been configured.
            if (config.getDir() == null) {
                throw new IOException("Please set the dir in the config file");
            }

            readPasswordConfig();
        } catch (IOException e) {
            throw new RuntimeException("Error parsing config file ", e);
        }
    }

    private void readConfigString(String configString) {
        // Read Config File
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try {
            config = mapper.readValue(configString, Configuration.class);
            config.setConfigFile("");
            // Checking if the security directory has been configured.
            if (config.getDir() == null) {
                throw new IOException("Please set the dir in the config file");
            }

            readPasswordConfig();
        } catch (IOException e) {
            throw new RuntimeException("Error parsing config file ", e);
        }
    }

    public static void main(String[] args) throws Exception {
        OPCUAServer serverInit = new OPCUAServer(args[0], true);
        serverInit.getServer().startup().get();
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.get();
    }

    public OPCUAServer(String configFile, boolean isFile) throws IOException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        if (isFile) {
            readConfigFile(configFile);
        } else {
            readConfigString(configFile);
        }

        File securityTempDir = new File(config.getDir(), "security");
        if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            throw new RuntimeException(
                    "Unable to create directory please confirm folder permissions on " + securityTempDir);
        }
        logger.info("Security Directory is: {}", securityTempDir.getAbsolutePath()); //

        File pkiDir = FileSystems.getDefault().getPath(config.getDir()).resolve("pki").toFile();
        DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
        logger.info(
                "Certificate directory is: {}, Please move certificates from the reject dir to the trusted directory to allow encrypted access",
                pkiDir.getAbsolutePath());

        DefaultServerCertificateValidator certificateValidator = new DefaultServerCertificateValidator(
                trustListManager);

        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(true, authChallenge -> {
            boolean check = passwordConfig.checkPassword(authChallenge.getUsername(), authChallenge.getPassword());
            if (!check) {
                logger.info("Invalid password for user:- {}", authChallenge.getUsername());
            }
            return check;
        });
        Security.addProvider(new BouncyCastleProvider());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        File serverKeyStore = securityTempDir.toPath().resolve(certificateFileName).toFile();

        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        CertificateKeyPair certificate = null;
        if (!serverKeyStore.exists()) {
            certificate = CertificateGenerator.generateCertificate();
            logger.info("Creating new KeyStore at {}", serverKeyStore);
            keyStore.load(null, passwordConfig.getSecurityPassword().toCharArray());
            keyStore.setKeyEntry("yofciot-certificate-alias", certificate.getKeyPair().getPrivate(),
                    passwordConfig.getSecurityPassword().toCharArray(),
                    new X509Certificate[] { certificate.getCertificate() });
            keyStore.store(new FileOutputStream(serverKeyStore), passwordConfig.getSecurityPassword().toCharArray());
        } else {
            logger.info("Loading KeyStore at {}", serverKeyStore);
            keyStore.load(new FileInputStream(serverKeyStore), passwordConfig.getSecurityPassword().toCharArray());
            String alias = keyStore.aliases().nextElement();
            KeyPair kp = new KeyPair(keyStore.getCertificate(alias).getPublicKey(),
                    (PrivateKey) keyStore.getKey(alias, passwordConfig.getSecurityPassword().toCharArray()));
            certificate = new CertificateKeyPair(kp, (X509Certificate) keyStore.getCertificate(alias));
        }

        String applicationUri = CertificateUtil.getSanUri(certificate.getCertificate())
                .orElseThrow(() -> new UaRuntimeException(StatusCodes.Bad_ConfigurationError,
                        "certificate is missing the application URI"));

        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();
        List<String> bindAddresses = new ArrayList<>();
        bindAddresses.add("0.0.0.0");
        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        for (String bindAddress : bindAddresses) {
            for (String hostname : hostnames) {
                EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder().setBindAddress(bindAddress)
                        .setHostname(hostname).setPath("/iot").setCertificate(certificate.getCertificate())
                        .setBindPort(config.getTcpPort()).setSecurityMode(MessageSecurityMode.None).addTokenPolicies(
                                USER_TOKEN_POLICY_ANONYMOUS, USER_TOKEN_POLICY_USERNAME, USER_TOKEN_POLICY_X509);

                endpointConfigurations.add(builder.copy().setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.SignAndEncrypt).build());

                endpointConfigurations.add(builder.copy().setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.SignAndEncrypt).build());

                EndpointConfiguration.Builder discoveryBuilder = builder.copy().setPath("/discovery")
                        .setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(discoveryBuilder.build());

                EndpointConfiguration.Builder discoveryLocalBuilder = builder.copy().setPath("/discovery")
                        .setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(discoveryLocalBuilder.build());

                EndpointConfiguration.Builder discoveryLocalPlc4xBuilder = builder.copy().setPath("/iot/discovery")
                        .setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(discoveryLocalPlc4xBuilder.build());

                if (!config.getDisableInsecureEndpoint()) {
                    EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setTransportProfile(TransportProfile.TCP_UASC_UABINARY);
                    endpointConfigurations.add(noSecurityBuilder.build());
                }

                // Always add an unsecured endpoint to localhost, this is a work around for Milo throwing an exception
                // if it
                // isn't here.
                EndpointConfiguration.Builder noSecurityBuilder = builder.copy().setSecurityPolicy(SecurityPolicy.None)
                        .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                        .setSecurityMode(MessageSecurityMode.None);
                endpointConfigurations.add(noSecurityBuilder.build());
            }
        }

        DefaultCertificateManager certificateManager = new DefaultCertificateManager(certificate.getKeyPair(),
                // Added so that existing certificates are loaded on startup
                Arrays.stream(keyStore.getCertificateChain(keyStore.getCertificateAlias(certificate.getCertificate())))
                        .map(X509Certificate.class::cast).toArray(X509Certificate[]::new));

        @SuppressWarnings("unchecked")
        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder().setApplicationUri(applicationUri)
                .setApplicationName(LocalizedText.english(applicationUri)).setEndpoints(endpointConfigurations)
                .setBuildInfo(new BuildInfo("urn:com:yofc:iot:server", "com.yofc.iot", config.getName(),
                        OpcUaServer.SDK_VERSION, "", DateTime.now()))
                .setCertificateManager(certificateManager).setTrustListManager(trustListManager)
                .setCertificateValidator(certificateValidator)
                .setIdentityValidator(new CompositeValidator<>(identityValidator, x509IdentityValidator))
                .setProductUri("urn:com:yofc:iot:server").build();

        server = new OpcUaServer(serverConfig);

        plc4xNamespace = new Plc4xNamespace(server, config);
        plc4xNamespace.startup();

    }

    public OpcUaServer getServer() {
        return server;
    }

    public CompletableFuture<OPCUAServer> startup() {
        return server.startup().thenApply(s -> this);
    }

    public CompletableFuture<OPCUAServer> shutdown() {
        plc4xNamespace.shutdown();
        return server.shutdown().thenApply(s -> this);
    }

}
