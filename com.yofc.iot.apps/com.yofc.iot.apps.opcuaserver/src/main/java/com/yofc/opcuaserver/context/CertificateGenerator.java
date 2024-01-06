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

package com.yofc.opcuaserver.context;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateGenerator.class);
    private static final String APPURI = "urn:com:yofc:iot:server";

    private CertificateGenerator() {
        throw new IllegalStateException("Utility class");
    }

    public static CertificateKeyPair generateCertificate() {
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Security Algorithim is unsupported for certificate");
        }
        if (kpg == null) {
            return null;
        }
        kpg.initialize(2048);
        kpg.generateKeyPair();
        X500NameBuilder nameBuilder = new X500NameBuilder();

        nameBuilder.addRDN(BCStyle.CN, "YOFC");
        nameBuilder.addRDN(BCStyle.O, "长飞光纤光缆股份有限公司");
        nameBuilder.addRDN(BCStyle.OU, "信息技术部");
        nameBuilder.addRDN(BCStyle.L, "武汉");
        nameBuilder.addRDN(BCStyle.ST, "湖北");
        nameBuilder.addRDN(BCStyle.C, "中国");
        byte[] bytes = new byte[40];
        ThreadLocalRandom.current().nextBytes(bytes);
        BigInteger serial = new BigInteger(bytes);

        final Calendar calender = Calendar.getInstance();
        calender.add(Calendar.DATE, -1);
        Date startDate = calender.getTime();
        calender.add(Calendar.DATE, 365 * 25);
        Date expiryDate = calender.getTime();

        KeyPairGenerator generator = null;
        try {
            generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();

            SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo
                    .getInstance(keyPair.getPublic().getEncoded());

            X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(nameBuilder.build(), serial,
                    startDate, expiryDate, Locale.ENGLISH, nameBuilder.build(), subjectPublicKeyInfo);

            GeneralName[] gnArray = new GeneralName[] {
                    new GeneralName(GeneralName.dNSName, InetAddress.getLocalHost().getHostName()),
                    new GeneralName(GeneralName.uniformResourceIdentifier, APPURI) };

            GeneralNames subjectAltNames = GeneralNames.getInstance(new DERSequence(gnArray));
            certificateBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

            ContentSigner sigGen = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC")
                    .build(keyPair.getPrivate());

            X509CertificateHolder certificateHolder = certificateBuilder.build(sigGen);

            JcaX509CertificateConverter certificateConvertor = new JcaX509CertificateConverter();
            certificateConvertor.setProvider(new BouncyCastleProvider());

            return new CertificateKeyPair(keyPair, certificateConvertor.getCertificate(certificateHolder));

        } catch (Exception e) {
            LOGGER.error("Security Algorithim is unsupported for certificate");
            return null;
        }
    }
}
