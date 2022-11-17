package edu.zju.cst.aces;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class TestJsonParser {

    public static void main(String[] args) throws IOException {
        String path = "D:\\IdeaProjects\\JavaCallgraph\\src\\test\\resources\\test.conf";
        File classFile = new File(path);
        String fileContent = FileUtils.readFileToString(classFile);
        System.out.println(fileContent);
        JSONObject object = JSON.parseObject(fileContent);
        System.out.println(object);
    }

}
