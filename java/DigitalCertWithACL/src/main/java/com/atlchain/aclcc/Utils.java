package com.atlchain.aclcc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class Utils {
//    public static void main(String[] args) {
//        boolean access = isAccessable("140000", "140130");
//        System.out.println(access);
//    }

    public static String getRegioncode(byte[] userByte) {
        String certStr = new String(userByte);
        System.out.println("certStr: \n" + certStr);
        String[] strings = certStr.split("\n");
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : strings) {
            if (s.contains("BEGIN CERTIFICATE")) {
                stringBuilder.append("-----BEGIN CERTIFICATE-----" + "\n");
            } else {
                stringBuilder.append(s + "\n");
            }
        }

        String regioncode = null;
        try {
            InputStream inputStream = new ByteArrayInputStream(stringBuilder.toString().getBytes());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(inputStream);
            byte[] bytes = certificate.getExtensionValue("1.2.3.4.5.6.7.8.1");
            String extValue = new String(bytes);
            String str = extValue.split(",")[3];
            regioncode = str.substring(str.length() - 9, str.length() - 3);
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        return regioncode;
    }

    public static boolean isAccessable(String regionCode, String keyCode) {
        if( "0000".equals(regionCode.substring(2, 6)) && regionCode.substring(0, 2).equals(keyCode.substring(0, 2))){
            System.out.println("Provincial level viewing data");
            return true;
        }else if("00".equals(regionCode.substring(4, 6)) && regionCode.substring(0, 4).equals(keyCode.substring(0, 4))){
            System.out.println("City level view data");
            return true;
        }else if(regionCode.substring(0, 6).equals(keyCode)){
            System.out.println("County level view data");
            return true;
        }else{
            System.out.println("The certificate does not match, please enter the correct code");
            return false;
        }
    }
}
