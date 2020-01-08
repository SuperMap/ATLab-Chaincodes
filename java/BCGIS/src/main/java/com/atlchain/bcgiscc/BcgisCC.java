package com.atlchain.bcgiscc;

import io.netty.handler.ssl.OpenSsl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

public class BcgisCC extends ChaincodeBase {
    private static Log _logger = LogFactory.getLog(BcgisCC.class);

    public static void main(String[] args) {
        System.out.println("OpenSSL avaliable: " + OpenSsl.isAvailable());
        System.setProperty("file.encoding","UTF-8");

        try {
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null,null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        new BcgisCC().start(args);
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
            if (func.equals("GetRecordByMapname")) {
                return getRecordByMapname(stub, params);
            }
            if (func.equals("GetHistoryByKey")) {
                return getHistoryByKey(stub, params);
            }
            if (func.equals("GetRecordByKeyRange")) {
                return getRecordByKeyRange(stub, params);
            }
            if (func.equals("GetRecordByKeyRangeByte")) {
                return getRecordByKeyRangeByte(stub, params);
            }
            if (func.equals("GetRecordByKeyRangeByteNoBase64")) {
                return getRecordByKeyRangeByteNoBase64(stub, params);
            }
            if (func.equals("GetRecordBySelector")) {
                return getRecordBySelector(stub, params);
            }
            if(func.equals("DeleteRecordByKey")){
                return deleteRecordByKey(stub, params);
            }
            return newErrorResponse("Invalid invoke function name.");
        } catch (Throwable e) {
            return newErrorResponse(e);
        }
    }

    private Response putRecord(ChaincodeStub stub, List<String> args){
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String uuid = args.get(0);
        String jsonStr = args.get(1);
        stub.putStringState(uuid, jsonStr);
        return newSuccessResponse("Invoke finished successfully.");
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

    private Response getRecordByKey(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String key = args.get(0);
        byte[] val = stub.getState(key);
        if (val == null) {
            return newErrorResponse(String.format("Error: state for %s is null", key));
        }
        String message = "Query key->\"" + key + "\" successfully";
        return newSuccessResponse(message, val);
    }

    private Response getRecordByMapname(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded) {
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String mapname = args.get(0);
        QueryResultsIterator<KeyValue> resultsIterator = stub.getQueryResult("{\"selector\":{\"filename\":\"" + mapname + "\"}}");

        Iterator<KeyValue> iter = resultsIterator.iterator();
        StringBuilder strBuilder = new StringBuilder("");
        strBuilder.append("[");
        boolean shouldAddComma = false;
        while(iter.hasNext())
        {
            if(shouldAddComma){
                strBuilder.append(",");
            }
            KeyValue kval = iter.next();
            strBuilder.append("{\"Key\":\"" + kval.getKey() + "\",\"Record\":" + kval.getStringValue() + "}");
            shouldAddComma = true;
        }
        strBuilder.append("]");
        String message = "Query mapname->\"" + mapname + "\" successfully";
        return newSuccessResponse(message, strBuilder.toString().getBytes());
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

    // TODO should use getStateByRangeWithPagination
    private Response getRecordByKeyRange(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String startKey = args.get(0);
        String endKey = args.get(1);
        StringBuilder strBuilder = new StringBuilder("");
        boolean shouldAddComma = false;
        QueryResultsIterator<KeyValue> Results = stub.getStateByRange(startKey, endKey);
        Iterator<KeyValue> iter = Results.iterator();
        while(iter.hasNext())
        {
            if(shouldAddComma){
                strBuilder.append(",");
            }
            KeyValue keyValue = iter.next();
            strBuilder.append(keyValue.getStringValue());
            shouldAddComma = true;
        }

        String message = "Query key->\"" + argsNeeded + "\" successfully";
        return newSuccessResponse(message, strBuilder.toString().getBytes());
    }

    private Response getRecordByKeyRangeByte(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String startKey = args.get(0);
        String endKey = args.get(1);
        StringBuilder strBuilder = new StringBuilder("");
        boolean shouldAddComma = false;
        QueryResultsIterator<KeyValue> Results = stub.getStateByRange(startKey, endKey);
        Iterator<KeyValue> iter = Results.iterator();
        while(iter.hasNext())
        {
            if(shouldAddComma){
                strBuilder.append(",");
            }
            KeyValue keyValue = iter.next();
            strBuilder.append(Base64.getEncoder().encodeToString(keyValue.getValue()));
            shouldAddComma = true;
        }

        String message = "Query key->\"" + argsNeeded + "\" successfully";
        return newSuccessResponse(message, strBuilder.toString().getBytes());
    }

    // TODO Try to get information without base64 encoding
    private Response getRecordByKeyRangeByteNoBase64(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String startKey = args.get(0);
        String endKey = args.get(1);
        StringBuilder strBuilder = new StringBuilder("");
        boolean shouldAddComma = false;
        QueryResultsIterator<KeyValue> Results = stub.getStateByRange(startKey, endKey);
        Iterator<KeyValue> iter = Results.iterator();
        while(iter.hasNext())
        {
            if(shouldAddComma){
                strBuilder.append(",");
            }
            KeyValue keyValue = iter.next();
            strBuilder.append(keyValue.getValue());
            shouldAddComma = true;
        }

        String message = "Query key->\"" + argsNeeded + "\" successfully";
        return newSuccessResponse(message, strBuilder.toString().getBytes());
    }

    private Response getRecordBySelector(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String queryString = "{\"selector\":" + args.get(0)+ "}";
        QueryResultsIterator<KeyValue> queryResultsIterator = stub.getQueryResult(queryString);
        Iterator<KeyValue> iterator = queryResultsIterator.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        boolean bArrayMemberAlreadyWritten = false;
        while (iterator.hasNext()) {
            KeyValue kv = iterator.next();
            if (bArrayMemberAlreadyWritten) {
                stringBuilder.append(",");
            }
            stringBuilder.append("{\"Key\":\"");
            stringBuilder.append(kv.getKey());
            stringBuilder.append("\", \"Record\":");
            stringBuilder.append(kv.getStringValue());
            stringBuilder.append("}");
            bArrayMemberAlreadyWritten = true;
        }
        stringBuilder.append("]");
        System.out.println("result: " + stringBuilder.toString());
        return newSuccessResponse("success", stringBuilder.toString().getBytes());
    }

    private Response deleteRecordByKey(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String key = args.get(0);
        stub.delState(key);
        return newSuccessResponse("delete finished successfully");
    }

}
