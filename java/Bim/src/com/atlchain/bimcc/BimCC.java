package com.atlchain.bimcc;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ResponseUtils;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

public class BimCC extends ChaincodeBase {

    private static Log _logger = LogFactory.getLog(BimCC.class);

    @Override
    public Response init(ChaincodeStub stub) {
        return ResponseUtils.newSuccessResponse("BimCC instantiated successfully.");
    }

    @Override
    public Response invoke(ChaincodeStub stub) {
        try {
            _logger.info("Invoke java simple chaincode");
            String func = stub.getFunction();
            List<String> params = stub.getParameters();
            if (func.equals("PutRecord")) {
                return putRecord(stub, params);
            }
            if (func.equals("DelRecord")) {
                return delRecord(stub, params);
            }
            if (func.equals("GetRecord")) {
                return getRecord(stub, params);
            }
            if (func.equals("GetRecordBySelector")) {
                return getRecordBySelector(stub, params);
            }
			if (func.equals("GetHistoryByKey")) {
				return getHistoryByKey(stub, params);
			}
            return ResponseUtils.newErrorResponse("Invalid invoke function name. Expecting one of: " +
					"[\"PutRecord\", \"DelRecord\", \"GetRecord\", \"GetHistoryByKey\", \"GetRecordBySelector\"]");
        } catch (Throwable e) {
            return ResponseUtils.newErrorResponse(e);
        }
    }

    private Response putRecord(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 2;
        if (args.size() != argsNeeded) {
            return ResponseUtils.newErrorResponse("Incorrect number of arguments.Got" + args.size()
					+ ", Expecting " + argsNeeded);
        }

        // bim模型数据存储格式 MID-SID -> {"MID":MID, "SID":SID, "SHash":Hash}
        // MID-SID 整体模型ID和单个模型ID在中间加一个横线；
        // SHash 单个模型哈希值
        String key = args.get(0);
        String value = args.get(1);
        _logger.info(String.format("info of %s: %s", key, value));

        stub.putStringState(key, value);
        _logger.info("model saved");
        return ResponseUtils.newSuccessResponse("success");
    }

    private Response delRecord(ChaincodeStub stub, List<String> args) {
		int argsNeeded = 1;
		if (args.size() != argsNeeded) {
            return ResponseUtils.newErrorResponse("Incorrect number of arguments.Got" + args.size()
					+ ", Expecting " + argsNeeded);
		}
        String key = args.get(0);
        stub.delState(key);
        return ResponseUtils.newSuccessResponse("success");
    }

    private Response getRecord(ChaincodeStub stub, List<String> args) {
		int argsNeeded = 1;
		if (args.size() != argsNeeded) {
            return ResponseUtils.newErrorResponse("Incorrect number of arguments. Expecting id of the model to query");
        }
        String modelId = args.get(0);
        //byte[] stateBytes
        String val = stub.getStringState(modelId);
        if (val == null) {
            return ResponseUtils.newErrorResponse(String.format("Error: model info for %s is null", modelId));
        }
        _logger.info(String.format("Query Response:\nModel: %s, Model Info: %s\n", modelId, val));
        return ResponseUtils.newSuccessResponse("success", val.getBytes());
    }

    private Response getRecordBySelector(ChaincodeStub stub, List<String> args) {
        int argsNeeded = 1;
        if (args.size() != argsNeeded){
            return ResponseUtils.newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
        }

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
        return ResponseUtils.newSuccessResponse("success", stringBuilder.toString().getBytes());
    }

    private Response getHistoryByKey(ChaincodeStub stub, List<String> args) {
		int argsNeeded = 1;
		if (args.size() != argsNeeded){
			return ResponseUtils.newErrorResponse("Incorrect number of arguments.Got" + args.size() + ", Expecting " + argsNeeded);
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
			strBuilder.append("{\"TxId\":\"" + keyModification.getTxId() + "\",\"Record\":[" + keyModification.getStringValue() + "],\"Timestamp\":\""+ keyModification.getTimestamp() + "\",\"IsDeleted\":\"" + keyModification.isDeleted() + "\"}");
			shouldAddComma = true;
		}
		strBuilder.append("]");
		String message = "Query history of key->\"" + key + "\" successfully";
		return ResponseUtils.newSuccessResponse(message, strBuilder.toString().getBytes());
	}

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
        new BimCC().start(args);
    }
}
