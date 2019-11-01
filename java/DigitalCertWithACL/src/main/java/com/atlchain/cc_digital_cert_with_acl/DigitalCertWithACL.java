package com.atlchain.cc_digital_cert_with_acl;

import io.netty.handler.ssl.OpenSsl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class DigitalCertWithACL extends ChaincodeBase {
    private static Log _logger = LogFactory.getLog(com.atlchain.cc_digital_cert_with_acl.DigitalCertWithACL.class);

    public static void main(String[] args) {
        System.out.println("OpenSSL avaliable: " + OpenSsl.isAvailable());
        new com.atlchain.cc_digital_cert_with_acl.DigitalCertWithACL().start(args);
    }

    @Override
    public Response init(ChaincodeStub stub) {
        return newSuccessResponse();
    }

    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            _logger.info("Invoke java BCGIS chaincode");
            String func = stub.getFunction();
            List<String> params = stub.getParameters(); // include function name
            List<byte[]> paramsByte = stub.getArgs();   // not include function name

            if (func.equals("PutRecord")) {
                return putRecord(stub, params);
            }
            if (func.equals("PutRecordBytes")) {
                return putRecordBytes(stub, paramsByte);
            }
            if (func.equals("GetRecordByKey")) {
                return getRecordByKey(stub, params);
            }
            if (func.equals("GetHistoryByKey")) {
                return getHistoryByKey(stub, params);
            }
            return newErrorResponse("Invalid invoke function name.");
        } catch (Throwable e) {
            return newErrorResponse(e);
        }
    }

    private Response putRecordBytes(ChaincodeStub stub, List<byte[]> args){
        int argsNeeded = 3;
        if (args.size() != 3){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String putKey = new String(args.get(1));
        byte[] byteArray = args.get(2);
        stub.putState(putKey, byteArray);
        return newSuccessResponse("Invoke finished successfully");
    }

    private Response getHistoryByKey(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String key = args.get(0);
        StringBuilder strBuilder = new StringBuilder("");
        strBuilder.append("[");
        boolean shouldAddComma = false;
        QueryResultsIterator<KeyModification> resultsIterator = stub.getHistoryForKey(key);
        Iterator<KeyModification> iter = resultsIterator.iterator();

        while(iter.hasNext())
        {
            if(shouldAddComma){
                strBuilder.append(",");
            }
            KeyModification keyModification = iter.next();
            strBuilder.append("{\"TxId\":\"" + keyModification.getTxId() + "\",\"Record\":" + keyModification.getStringValue() + ",\"Timestamp\":\""+ keyModification.getTimestamp() + "\",\"IsDeleted\":\"" + keyModification.isDeleted() + "\"}");
            shouldAddComma = true;
        }
        strBuilder.append("]");
        String message = "Query history of key->\"" + key + "\" successfully";
        return newSuccessResponse(message, strBuilder.toString().getBytes());
    }

    private String getOu(byte[] userByte){
//        byte[] userByte = stub.getCreator();
        Response response = newSuccessResponse(userByte);
        String initString = response.getStringPayload();
        String[] strings = initString.split("\n");
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : strings) {
            if(s.contains("BEGIN CERTIFICATE")){
                stringBuilder.append("-----BEGIN CERTIFICATE-----" + "\n");
            }else {
                stringBuilder.append(s + "\n");
            }
        }
//        System.out.println(stringBuilder.toString());
        String ou = null;
        String tmpString;
        String textContent;
        try {
            InputStream inputStream = new ByteArrayInputStream(stringBuilder.toString().getBytes());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(inputStream);
            tmpString = certificate.getSubjectDN().toString();
            List<String> list = Arrays.asList(tmpString.split(","));
            textContent = list.get(1);
            textContent = textContent.trim();
            while (textContent.startsWith("　")) {
                textContent = textContent.substring(1, textContent.length()).trim();
            }
            ou = textContent.substring(3, textContent.length());
        }catch (CertificateException e) {
            e.printStackTrace();
        }
        System.out.println(ou);
        return ou;
    }

    private Response putRecord(ChaincodeStub stub, List<String> args){
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        // get ou
        byte[] userByte = stub.getCreator();
        String ou = getOu(userByte);
        System.out.println("ou: " + ou);

        String key = args.get(0);
        String value = args.get(1);
        stub.putStringState(key, value);
        return newSuccessResponse("Invoke finished successfully." );
    }

    private Response getRecordByKey(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String key = args.get(0);
        String OU = args.get(1);
        byte[] tmpVal = stub.getState(key);
        byte[] val = null;

        // get ou
        byte[] userByte = stub.getCreator();
        String ou = getOu(userByte);
        System.out.println("ou: " + ou);

        if( "0000".equals(OU.substring(2, 6)) && OU.substring(0, 2).equals(key.substring(0, 2))){
            System.out.println("Provincial level viewing data");
            val = tmpVal;
        }else if("00".equals(OU.substring(4, 6)) && OU.substring(0, 4).equals(key.substring(0, 4))){
            System.out.println("City level view data");
            val = tmpVal;
        }else if(OU.substring(0, 6).equals(key.substring(0, 6))){
            System.out.println("County level view data");
            val = tmpVal;
        }else{
            System.out.println("The certificate does not match, please enter the correct code");
        }
        if (val == null) {
            return newErrorResponse(String.format("Error: state for %s is null", key));
        }
        String message = "Query key->\"" + key + "\" successfully";
        return newSuccessResponse(message, val);
    }
}