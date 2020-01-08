package com.atlchain.aclcc;

import com.alibaba.fastjson.JSONObject;
import io.netty.handler.ssl.OpenSsl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

public class AclCC extends ChaincodeBase {
    private static Log _logger = LogFactory.getLog(com.atlchain.cc_digital_cert_with_acl.DigitalCertWithACL.class);

    public static void main(String[] args) {
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
        System.out.println("OpenSSL avaliable: " + OpenSsl.isAvailable());
        new com.atlchain.cc_digital_cert_with_acl.DigitalCertWithACL().start(args);
    }

    @Override
    public Response init(ChaincodeStub stub) {
        _logger.info("Init java simple chaincode");
        String func = stub.getFunction();
        if (!func.equals("init")) {
            return newErrorResponse("function other than init is not supported");
        }

        return newSuccessResponse();
    }

    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            _logger.info("Invoke java aclcc chaincode");
            String func = stub.getFunction();
            System.out.println("invoke Function: " + func);

            List<String> params = stub.getParameters(); // include function name
            List<byte[]> paramsByte = stub.getArgs();   // not include function name

            if (func.equals("PutRecord")) {
                return putRecord(stub, params);
            }
//            if (func.equals("PutRecordBytes")) {
//                return putRecordBytes(stub, paramsByte);
//            }
            if (func.equals("GetRecordBySelector")) {
                return getRecordBySelector(stub, params);
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

    private Response putRecord(ChaincodeStub stub, List<String> args){
        int argsNeeded = 2;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String putKey = args.get(0);
        String jsonStr = args.get(1);

        JSONObject jsonObject = JSONObject.parseObject(jsonStr);


        System.out.println("putKey: " + putKey);
        System.out.println("jsonStr: " + jsonStr);

        // get regioncode
        byte[] userByte = stub.getCreator();
        String regionCode = Utils.getRegioncode(userByte);
        jsonObject.put("regioncode", regionCode);

        stub.putStringState(putKey, jsonObject.toJSONString());
        return newSuccessResponse("Successfully");
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

    private Response getRecordBySelector(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }

        byte[] userByte = stub.getCreator();
        String regioncode = Utils.getRegioncode(userByte);
        System.out.println("regionCode: " +  regioncode);

        //queryString := "{\"selector\":{\"Hash\":\"" + hash + "\"}}"
        String queryString = "{\"selector\":" + args.get(0)+ "}";
        System.out.println("queryString: " + queryString);
        QueryResultsIterator<KeyValue> queryResultsIterator = stub.getQueryResult(queryString);
        Iterator<KeyValue> iterator = queryResultsIterator.iterator();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        boolean bArrayMemberAlreadyWritten = false;
        while (iterator.hasNext()) {
            KeyValue kv = iterator.next();
            System.out.println("kv :" + kv.getKey());
            System.out.println("kv :" + kv.getStringValue());
            if (!Utils.isAccessable(regioncode, JSONObject.parseObject(kv.getStringValue()).get("regioncode").toString() )) {
                continue;
            }

            if (bArrayMemberAlreadyWritten == true) {
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
        return newSuccessResponse("Successfully", stringBuilder.toString().getBytes());
    }

    private Response getRecordByKey(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }
        String key = args.get(0);
        byte[] tmpVal = stub.getState(key);
        byte[] val = null;

        System.out.println("getState: " + new String(tmpVal));

        // get regioncode
        byte[] userByte = stub.getCreator();
        String regioncode = Utils.getRegioncode(userByte);

        JSONObject jsonObject = JSONObject.parseObject(new String(tmpVal));

        if (Utils.isAccessable(regioncode, jsonObject.get("regioncode").toString())) {
            val = tmpVal;
        } else {
            return newErrorResponse(String.format("Error: state for %s is null", key));
        }
        String message = "Query key->\"" + key + "\" successfully";
        return newSuccessResponse(message, val);
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
}
// 99ad40afa494c305c12e8d07b25221c152848f2806d33837aa6eba906b39b281