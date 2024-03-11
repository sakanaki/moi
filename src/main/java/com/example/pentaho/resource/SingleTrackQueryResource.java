package com.example.pentaho.resource;


import com.example.pentaho.component.IbdTbIhChangeDoorplateHis;
import com.example.pentaho.component.SingleBatchQueryParams;
import com.example.pentaho.component.User;
import com.example.pentaho.exception.MoiException;
import com.example.pentaho.service.SingleTrackQueryService;
import com.example.pentaho.utils.UserContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/single-track-query")
public class SingleTrackQueryResource {

    private Logger logger = LoggerFactory.getLogger(SingleTrackQueryResource.class);


    @Autowired
    private SingleTrackQueryService singleQueryTrackService;


    /***
     * 單筆查詢軌跡
     */
    @Operation(description = "單筆軌跡",
            parameters = {@Parameter(in = ParameterIn.HEADER,
                    name = "Authorization",
                    description = "jwt token,body附帶 userInfo={\"Id\":1,\"orgId\":\"Admin\"}",
                    required = true,
                    schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "",
                            content = @Content(schema = @Schema(implementation = List.class), examples = @ExampleObject(value = "[\n" +
                                    "    {\n" +
                                    "        \"addressId\": \"BSZ7538-0\",\n" +
                                    "        \"hisAdr\": \"10010020臺灣省嘉義縣朴子市大葛里027鄰祥和三路西段２３號三樓３９\",\n" +
                                    "        \"wgsX\": \"120.289578060453740\",\n" +
                                    "        \"wgsY\": \"23.455075446688465\",\n" +
                                    "        \"updateType\": \"行政區域調整\",\n" +
                                    "        \"updateDt\": \"20200701\"\n" +
                                    "    }\n" +
                                    "]"))),
                    @ApiResponse(responseCode = "500", description = "",
                            content = @Content(schema = @Schema(implementation = String.class), examples = @ExampleObject(value = ""))
                    )})
    @PostMapping("/query-track")
    public ResponseEntity<List<IbdTbIhChangeDoorplateHis>> queryTrack(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "編碼",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = "BSZ7538-0")
                    )
            )
            @RequestBody String addressId) {
        return new ResponseEntity<>(singleQueryTrackService.querySingleTrack(addressId), HttpStatus.OK);
    }


    @Operation(description = "單筆軌跡批次",
            parameters = {@Parameter(in = ParameterIn.HEADER,
                    name = "Authorization",
                    description = "jwt token,body附帶 userInfo={\"Id\":1,\"orgId\":\"Admin\"}",
                    required = true,
                    schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "作業開始執行"),
                    @ApiResponse(responseCode = "500", description = "檔案為空 或 非CSV檔"
                    )})
    @PostMapping("/query-batch-track")
    public void queryBatchTrack(@Parameter(
            description = "批次ID",
            required = true,
            schema = @Schema(type = "string"))
                                    @RequestParam("Id") String Id,
                                @Parameter(
                                        description = "原始CSVID",
                                        required = true,
                                        schema = @Schema(type = "string"))
                                    @RequestParam("originalFileId") String originalFileId,
                                @Parameter(
                                        description = "申請單號",
                                        required = true,
                                        schema = @Schema(type = "string"))
                                    @RequestParam("formName") String formName,
                                @Parameter(
                                        description = "使用者上傳的CSV檔",
                                        required = true
                                )
                                    @RequestParam("file") MultipartFile file) throws IOException {
        logger.info("Received request");
        /**check**/
        if (file == null) {
            throw new MoiException("檔案為空");
        }

        if (!file.getOriginalFilename().endsWith(".csv")) {
            throw new MoiException("檔案須為CSV");
        }
        logger.info("start processing");
        SingleBatchQueryParams singleBatchQueryParams = new SingleBatchQueryParams(Id, originalFileId, "0", "SYS_FAILED", formName + ".csv");
        singleQueryTrackService.queryBatchTrack(file, singleBatchQueryParams);
    }


    /**
     * 單筆未登入測試
     */
    @Operation(description = "單筆未登入測試")
    @PostMapping("/forguest")
    public ResponseEntity<String> forGuestUser(){
    return new ResponseEntity<>("用戶未登入",HttpStatus.OK);
    }

    /**
     * 單筆APIKEY測試
     */
    @Operation(description = "單筆APIKEY測試",
            parameters = {@Parameter(in = ParameterIn.HEADER,
                    name = "Authorization",
                    description = "資拓私鑰加密的jwt token",
                    required = true,
                    schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "userId",
                            content = @Content(schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "500", description = ""),
            })
    @PostMapping("/forapikey")
    public ResponseEntity<String> forAPIKeyUser(){
        User user = UserContextUtils.getUserHolder();
        logger.info("user:{}",user);
        return new ResponseEntity<>(user.getId(),HttpStatus.OK);
    }
}