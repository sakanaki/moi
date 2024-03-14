package com.example.pentaho.service;

import com.example.pentaho.component.*;
import com.example.pentaho.repository.IbdTbAddrCodeOfDataStandardRepository;
import com.example.pentaho.repository.IbdTbAddrDataNewRepository;
import com.example.pentaho.repository.IbdTbIhChangeDoorplateHisRepository;
import com.example.pentaho.utils.AddressParser;
import com.example.pentaho.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.pentaho.utils.NumberParser.replaceWithHalfWidthNumber;

@Service
public class SingleQueryService {

    private static Logger log = LoggerFactory.getLogger(SingleQueryService.class);

    Integer SCAN_SIZE = 1000;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private IbdTbIhChangeDoorplateHisRepository ibdTbIhChangeDoorplateHisRepository;

    @Autowired
    private IbdTbAddrDataNewRepository ibdTbAddrDataNewRepository;

    @Autowired
    private AddressParser addressParser;

    @Autowired
    private IbdTbAddrCodeOfDataStandardRepository ibdTbAddrCodeOfDataStandardRepository;

    String segmentExistNumber = ""; //紀錄user是否有輸入每個地址片段，有:1，沒有:0

    /**
     * 找單一個值 (redis: get)
     */
    public String findByKey(String columnName, String key, String defaultValue) {
        if (key != null && !key.isEmpty()) {
            String redisValue = stringRedisTemplate.opsForValue().get(key);
            if (redisValue != null && !redisValue.isEmpty()) {
                log.info("columnName:{} , redisKey: {} , redisValue: {}", columnName, key, redisValue);
                segmentExistNumber += "1";
                return redisValue;
            }
        }
        segmentExistNumber += "0";
        return defaultValue;
    }


    /**
     * 找為LIST的值 (redis: LRANGE)
     */
    public List<String> findListByKey(String key) {
        ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
        List<String> elements = listOps.range(key, 0, -1);
        log.info("elements:{}", elements);
        return elements;
    }

    /**
     * 模糊比對，找出相符的 KEY (redis: scan)
     */
    public Set<String> findListByScan(String key) {
        Set<String> keySet = stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keySetTemp = new ConcurrentSkipListSet<>();
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(key) //模糊比對
                    .count(SCAN_SIZE)
                    .build())) {
                while (cursor.hasNext() && keySetTemp.size() < SCAN_SIZE) {
                    keySetTemp.add(new String(cursor.next(), "utf-8"));
                }
            } catch (Exception e) {
                log.error("redis，模糊比對錯誤:{}", e.getMessage());
            }
            return keySetTemp;
        });
        log.info("keySet:{}", keySet);
        return keySet;
    }


    /**
     * set單一個值 (redis: set)
     */
    public void setData(SingleQueryDTO singleQueryDTO) {
        stringRedisTemplate.opsForValue().set(singleQueryDTO.getRedisKey(), singleQueryDTO.getRedisValue());
        log.info("set單一個值，key: {}, value: {}", singleQueryDTO.getRedisKey(), singleQueryDTO.getRedisValue());
    }


    /**
     * 一個key塞多筆值(redis: RPUSH)
     */
    public void pushData(SingleQueryDTO singleQueryDTO) {
        stringRedisTemplate.opsForList().rightPushAll(singleQueryDTO.getRedisKey(), singleQueryDTO.getRedisValueList());
        log.info("push value to a key，key: {}, value: {}", singleQueryDTO.getRedisKey(), singleQueryDTO.getRedisValueList());
    }


    /**
     * 找為SET的值 (redis: SMEMBERS)
     */
    public Set<String> findSetByKey(SingleQueryDTO singleQueryDTO) {
        SetOperations<String, String> setOps = stringRedisTemplate.opsForSet();
        Set<String> elements = setOps.members(singleQueryDTO.getRedisKey());
        log.info("elements:{}", elements);
        return elements;
    }


    public String findJsonTest(SingleQueryDTO singleQueryDTO) {
        return findByKey(null, "1066693", null);
    }

    public List<IbdTbAddrCodeOfDataStandardDTO> findJson(String originalString) {
        Address address = findMappingId(originalString);
        log.info("mappingId:{}", address.getMappingId());
        Set<String> seqSet = finSeqByMappingIdInRedis(address);
        log.info("seq:{}", seqSet);
        List<IbdTbAddrCodeOfDataStandardDTO> list = ibdTbAddrCodeOfDataStandardRepository.findBySeq(seqSet.stream().map(Integer::parseInt).collect(Collectors.toList()));
        //放地址比對代碼
        list.forEach(IbdTbAddrDataRepositoryNewdto -> IbdTbAddrDataRepositoryNewdto.setJoinStep(address.getJoinStep()));
        return list;
    }

    public Address findMappingId(String originalString) {
        //切地址
        Address address = addressParser.parseAddress(originalString, null);
        if (address != null) {
            log.info("address:{}", address);
            segmentExistNumber = ""; //先清空
            String county = address.getCounty();
            //如果是別名，要找到正確的名稱

            address.setCountyCd(findByKey("countyCd", county, "00000"));

            String town = address.getTown();
            address.setTownCd(findByKey("townCd", county + ":" + town, "000"));//鄉鎮市區

            String village = address.getVillage(); //里
            address.setVillageCd(findByKey("villageCd", town + ":" + village, "000"));//里

            address.setNeighborCd(findNeighborCd(address.getNeighbor()));//鄰

            String road = address.getRoad();
            String area = address.getArea();

            address.setRoadAreaSn(findByKey("roadAreaSn", replaceWithHalfWidthNumber(road) + (area == null ? "" : area), "0000000"));

            String lane = address.getLane(); //巷
            address.setLaneCd(findByKey("laneCd", replaceWithHalfWidthNumber(lane), "0000"));//巷

            String alley = address.getAlley(); //弄
            String subAlley = address.getSubAlley(); //弄
            address.setAlleyIdSn(findByKey("alleyIdSn", replaceWithHalfWidthNumber(alley) + replaceWithHalfWidthNumber(subAlley), "0000000"));

            String numTypeCd = "95";
            segmentExistNumber += 1; //numTypeCd一定找的到，所以直接寫1
            //如果有"之45一樓"，要額外處理
            if (StringUtils.isNotNullOrEmpty(address.getContinuousNum())) {
                formatCoutinuousFlrNum(address.getContinuousNum(), address);
            }
            String numFlr1 = address.getNumFlr1();
            address.setNumFlr1Id(findByKey("NUM_FLR_1", "NUM_FLR_1:" + deleteBasementString(numFlr1), "000000"));

            String numFlr2 = address.getNumFlr2();
            address.setNumFlr2Id(findByKey("NUM_FLR_2", "NUM_FLR_2:" + deleteBasementString(numFlr2), "00000"));

            String numFlr3 = address.getNumFlr3();
            address.setNumFlr3Id(findByKey("NUM_FLR_3", "NUM_FLR_3:" + deleteBasementString(numFlr3), "0000"));

            String numFlr4 = address.getNumFlr4();
            address.setNumFlr4Id(findByKey("NUM_FLR_4", "NUM_FLR_4:" + deleteBasementString(numFlr4), "000"));

            String numFlr5 = address.getNumFlr5();
            address.setNumFlr5Id(findByKey("NUM_FLR_5", "NUM_FLR_5:" + deleteBasementString(numFlr5), "0"));

            String numFlrId = address.getNumFlr1Id() + address.getNumFlr2Id() + address.getNumFlr3Id() + address.getNumFlr4Id() + address.getNumFlr5Id();

            String basementStr = address.getBasementStr() == null ? "0" : address.getBasementStr();
            segmentExistNumber += 0; //basementStr一律當作找不到，去模糊比對

            //處理numFlrPos
            String numFlrPos = getNumFlrPos(address); //不會列入segmentExistNumber
            log.info("numFlrPos為:{}", numFlrPos);
            String room = address.getRoom(); //里
            String roomIdSn = findByKey("roomIdSn", replaceWithHalfWidthNumber(room), "00000");
            address.setMappingId(address.getCountyCd() + address.getTownCd() + address.getVillageCd() + address.getNeighborCd() + address.getRoadAreaSn() + address.getLaneCd() + address.getAlleyIdSn() + numTypeCd +
                    numFlrId + basementStr
                    + numFlrPos
                    + roomIdSn
            );
            List<String> mappingIdList = List.of(address.getCountyCd(), address.getTownCd(), address.getVillageCd(), address.getNeighborCd(), address.getRoadAreaSn(), address.getLaneCd(), address.getAlleyIdSn(), numTypeCd,
                    address.getNumFlr1Id(), address.getNumFlr2Id(), address.getNumFlr3Id(), address.getNumFlr4Id(), address.getNumFlr5Id(), basementStr
                    , numFlrPos
                    , roomIdSn);
            address.setMappingIdList(mappingIdList);
            log.info("segmentExistNumber:{}", segmentExistNumber);
            //刪除最後逗號
            int lastIndex = segmentExistNumber.lastIndexOf(",");
            if (lastIndex != -1) {
                segmentExistNumber = segmentExistNumber.substring(0, lastIndex);
            }
            address.setSegmentExistNumber(segmentExistNumber);
        }
        findCompareNumber(address);
        return address;
    }

    public List<IbdTbIhChangeDoorplateHis> singleQueryTrack(String addressId) {
        log.info("addressId:{}", addressId);
        return ibdTbIhChangeDoorplateHisRepository.findByAddressId(addressId);
    }


    public String findNeighborCd(String rawNeighbor) {
        if (StringUtils.isNotNullOrEmpty(rawNeighbor)) {
            Pattern pattern = Pattern.compile("\\d+"); //指提取數字
            Matcher matcher = pattern.matcher(replaceWithHalfWidthNumber(rawNeighbor));
            if (matcher.find()) {
                String neighborResult = matcher.group();
                // 往前補零，補到三位數
                String paddedNumber = String.format("%03d", Integer.parseInt(neighborResult));
                log.info("提取的數字部分為：{}", paddedNumber);
                segmentExistNumber += "1";
                return paddedNumber;
            }
        } else {
            log.info("沒有數字部分");
            segmentExistNumber += "0";
            return "000";
        }
        segmentExistNumber += "0";
        return "000";
    }

    public String deleteBasementString(String rawString) {
        if (rawString != null) {
            return replaceWithHalfWidthNumber(rawString).replace("basement:", "");
        }
        return "";
    }


    public String getNumFlrPos(Address address) {
        String[] patternFlr1 = {".+號$", ".+樓$", ".+之$"};
        String[] patternFlr2 = {".+號$", ".+樓$", ".+之$", "^之.+", ".+棟$", ".+區$", "^之.+號", "^[A-ZＡ-Ｚ]+$"};
        String[] patternFlr3 = {".+號$", ".+樓$", ".+之$", "^之.+", ".+棟$", ".+區$", "^[0-9０-９a-zA-Zａ-ｚＡ-Ｚ一二三四五六七八九東南西北甲乙丙]+$"};
        String[] patternFlr4 = {".+號$", ".+樓$", ".+之$", "^之.+", ".+棟$", ".+區$", "^[0-9０-９a-zA-Zａ-ｚＡ-Ｚ一二三四五六七八九東南西北甲乙丙]+$"};
        String[] patternFlr5 = {".+號$", ".+樓$", ".+之$", "^之.+", ".+棟$", ".+區$", "^[0-9０-９a-zA-Zａ-ｚＡ-Ｚ一二三四五六七八九東南西北甲乙丙]+$"};
        segmentExistNumber += 0 ;//numFlrPos一律當作找不到，去模糊比對
        return getNum(address.getNumFlr1(), patternFlr1) + getNum(address.getNumFlr2(), patternFlr2) +
                getNum(address.getNumFlr3(), patternFlr3) + getNum(address.getNumFlr4(), patternFlr4) +
                getNum(address.getNumFlr5(), patternFlr5);
    }


    private String getNum(String inputString, String[] patternArray) {
        if (inputString != null && !inputString.isEmpty()) {
            for (int i = 0; i < patternArray.length; i++) {
                Pattern pattern = Pattern.compile(patternArray[i]);
                Matcher matcher = pattern.matcher(inputString);
                if (matcher.matches()) {
                    return String.valueOf(i + 1);
                }
            }
        } else {
            return "0"; //如果沒有該片段地址，就補0
        }
        return "x";
    }


    Set<String> finSeqByMappingIdInRedis(Address address) {
        Set<String> seqSet = new HashSet<>();
        String seq = findByKey("mappingId 64碼", address.getMappingId(), null);
        if (!StringUtils.isNullOrEmpty(seq)) {
            seqSet.add(seq);
        } else {
            //如果找不到完整代碼，要用正則模糊搜尋
            Map<String,String> map = buildRegexMappingId(address);
            String newMappingId =  map.get("newMappingId");
            String regex =  map.get("regex");
            log.info("因為地址不完整，組成新的 mappingId {}，以利模糊搜尋",newMappingId);
            log.info("模糊搜尋正則表達式為:{}",regex);
            Set<String> mappingIdSet =  findListByScan(newMappingId);
            Pattern regexPattern = Pattern.compile(String.valueOf(regex));
            for (String newMapping : mappingIdSet) {
                //因為redis的scan命令，無法搭配正則，限制*的位置只能有多少字元，所以要再用java把不符合的mappinId刪掉
                Matcher matcher = regexPattern.matcher(newMapping);
                //有符合再進redis找seq
                if (matcher.matches()) {
                    String newSeq = findByKey("newMappingId", newMapping, null);
                    if (StringUtils.isNotNullOrEmpty(newSeq)) {
                        seqSet.add(newSeq);
                    }
                }
            }
        }
        return seqSet;
    }

    //處理: 之45一樓 (像這種連續的號碼，就會被歸在這裡)
    public void formatCoutinuousFlrNum(String input, Address address) {
        if (StringUtils.isNotNullOrEmpty(input)) {
            String firstPattern = "(?<coutinuousNum1>之[\\d\\uFF10-\\uFF19]+)(?<coutinuousNum2>\\D+樓)?"; //之45一樓
            String secondPattern = "(?<coutinuousNum1>之\\D+)(?<coutinuousNum2>[\\d\\uFF10-\\uFF19]+樓)?"; //之四五1樓
            Matcher matcherFirst = Pattern.compile(firstPattern).matcher(input);
            Matcher matcherSecond = Pattern.compile(secondPattern).matcher(input);
            String[] flrArray = {address.getNumFlr1(), address.getNumFlr2(), address.getNumFlr3(), address.getNumFlr4(), address.getNumFlr5()};
            int count = 0;
            for (int i = 0; i < flrArray.length; i++) {
                if (StringUtils.isNullOrEmpty(flrArray[i])) {
                    log.info("目前最大到:{}，新分解好的flr，就要再往後塞到:{}", "numFlr" + (i), "numFlr" + (i + 1));
                    count = i + 1;
                    break;
                }
            }
            if (matcherFirst.matches()) {
                setFlrNum(count, matcherFirst.group("coutinuousNum1"), matcherFirst.group("coutinuousNum2"), address);
            } else if (matcherSecond.matches()) {
                setFlrNum(count, matcherFirst.group("coutinuousNum1"), matcherFirst.group("coutinuousNum2"), address);
            }
        }
    }

    private void setFlrNum(int count, String first, String second, Address address) {
        first = replaceWithHalfWidthNumber(first);
        second = replaceWithHalfWidthNumber(second);
        log.info("first:{},second:{}", first, second);
        switch (count) {
            case 1:
                address.setNumFlr1(first);
                address.setNumFlr2(second);
                address.setNumFlr3(address.getAddrRemains()); //剩下跑到remain的就塞到最後
                break;
            case 2:
                address.setNumFlr2(first);
                address.setNumFlr3(second);
                address.setNumFlr4(address.getAddrRemains());//剩下跑到remain的就塞到最後
                break;
            case 3:
                address.setNumFlr3(first);
                address.setNumFlr4(second);
                address.setNumFlr5(address.getAddrRemains());//剩下跑到remain的就塞到最後
                break;
            case 4:
                address.setNumFlr4(first);
                address.setNumFlr5(second);
                break;
        }
    }

    private Map<String, String> buildRegexMappingId(Address address) {
        String segNum = address.getSegmentExistNumber();
        StringBuilder newMappingId = new StringBuilder(); //組給redis的模糊搜尋mappingId ex.10010***9213552**95********
        StringBuilder regex = new StringBuilder();   //組給java的模糊搜尋mappingId ex.10010020017029\d{18}95\d{19}000000\d{5}
        //mappingCount陣列代表，COUNTY_CD要5位元，TOWN_CD要3位元，VILLAGE_CD要3位元，以此類推
        //6,5,4,3,1 分別是NUM_FLR_1~NUM_FLR_5
        int[] mappingCount = {5, 3, 3, 3, 7, 4, 7, 2, 6, 5, 4, 3, 1, 1, 5, 5};
        int sum = 0;
        for (int i = 0; i < segNum.length(); i++) {
            //該欄位是1的情況(找的到的情況)
            if ("1".equals(String.valueOf(segNum.charAt(i)))) {
                newMappingId.append(address.getMappingIdList().get(i));
                regex.append(address.getMappingIdList().get(i));
                sum = 0; //歸零
            //該欄位是0的情況(找不到的情況)
            } else {
                String segAfter = "";
                String segBefore = "";
                //第一碼
                if(i == 0){
                    segAfter = String.valueOf(segNum.charAt(i+1));
                    //如果前後碼也是0的話，表示要相加
                    if("0".equals(segAfter)){
                        sum += mappingCount[i];
                    }
                    //後面那碼是1，表示不用相加了
                    else if("1".equals(segAfter)) {
                        sum += mappingCount[i];
                        regex.append("\\d{").append(sum).append("}");
                        sum = 0; //歸零
                    }
                }
                //不是最後一個
                else if (i != segNum.length() - 1) {
                    segAfter = String.valueOf(segNum.charAt(i + 1));
                    segBefore = String.valueOf(segNum.charAt(i - 1));
                    //如果前後都是是1的話，不用相加
                    if ("1".equals(segBefore) && "1".equals(segAfter)) {
                        sum = 0; //歸零
                        regex.append("\\d{").append(mappingCount[i]).append("}");
                    }
                    //如果前後碼也是0的話，表示要相加
                    else if("0".equals(segAfter)){
                        sum += mappingCount[i];
                    }
                    //後面那碼是1，表示不用相加了
                    else if("1".equals(segAfter)) {
                        sum += mappingCount[i];
                        regex.append("\\d{").append(sum).append("}");
                        sum = 0; //歸零
                    }
                }
                //是最後一個
                else {
                    segBefore = String.valueOf(segNum.charAt(i-1));
                    //如果前面是0表示最後一碼也要加上去
                    if("0".equals(segBefore)){
                        sum += mappingCount[i];
                        regex.append("\\d{").append(sum).append("}");
                    }else{
                        regex.append("\\d{").append(mappingCount[i]).append("}");
                    }
                    sum = 0; //歸零
                }
                newMappingId.append("*");
            }
        }
        Map<String, String> map = new HashMap<>();
        map.put("newMappingId", newMappingId.toString());
        map.put("regex", regex.toString());
        return map;
    }

    private void findCompareNumber(Address address){
        //排除鄰比對
        if ("000".equals(address.getNeighborCd())) {
            if (!"000".equals(address.getTownCd())) {
                address.setJoinStep("JA211"); //含有鄉鎮市區
            } else {
                address.setJoinStep("JA212"); //未含鄉鎮市區
            }
        }
        //排除村里比對
        if ("000".equals(address.getVillageCd())) {
            if (!"000".equals(address.getTownCd())) {
                address.setJoinStep("JA311"); //含有鄉鎮市區
            } else {
                address.setJoinStep("JA312"); //未含鄉鎮市區
            }
        }
    }

}
