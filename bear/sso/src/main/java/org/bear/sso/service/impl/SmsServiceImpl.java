package org.bear.sso.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.bear.common.JedisService;
import org.bear.exception.CustomException;
import org.bear.sso.repository.UserRepository;
import org.bear.sso.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Random;

@Service
public class SmsServiceImpl implements SmsService {

    @Autowired
    private UserRepository userRepository;

    public static  final String SMS_LOGIN="sms_login:";

    @Autowired
    private JedisService jedisService;

    //产品名称:云通信短信API产品,开发者无需替换
    static final String product = "Dysmsapi";
    //产品域名,开发者无需替换
    static final String domain = "dysmsapi.aliyuncs.com";

    static final String accessKeyId = "LTAIOWxgzN4SNbQP";
    static final String accessKeySecret = "A9ZXLnOVb1FInPOcW5enU2ENzAfRIR";

    private Logger LOGGER=LoggerFactory.getLogger(SmsServiceImpl.class);

    @Override
    public void sendLoginSms(String phone) throws CustomException {
        if(StringUtils.isEmpty(phone)){
            LOGGER.info("手机号码不能为空!");
            return;
        }
        if(userRepository.queryUserByPhone(phone)==null){
            LOGGER.info("手机号码未注册!{}",phone);
            throw  new CustomException("手机号码未注册!");
        }

        LOGGER.info("发送登录登录短信...");
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<4;i++){
            int n = new Random().nextInt(10);
            sb.append(n);
        }
        String number=sb.toString();
        LOGGER.debug("生成的验证码为{}",number);

        try {
            SendSmsResponse response = sendSms(phone, "SMS_135325318", number);

            LOGGER.debug("Code={}",response.getCode());
            if(!response.getCode().equals("OK")){
                  LOGGER.error("短信发送失败");
                 throw  new CustomException("短信发送失败,手机号码"+phone);
            }
            LOGGER.debug("Message={}",response.getMessage());
            LOGGER.debug("RequestId={}",response.getRequestId());
            LOGGER.debug("BizId={}",response.getBizId());
            //保存验证码到redis
            jedisService.set(SMS_LOGIN+phone,number);
            //设置过期时间
            jedisService.expire(SMS_LOGIN+phone,60*10);
        }catch (Exception e){
            e.printStackTrace();
            LOGGER.error("短信发送失败!,手机号码为{}",phone);
            throw  new CustomException("短信发送失败!");
        }

    }


    @Override
    public void sendRegisterSms(String phone) {

    }


    public static SendSmsResponse sendSms(String phone,String templateCode,String number) throws ClientException {

        //可自助调整超时时间
        System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
        System.setProperty("sun.net.client.defaultReadTimeout", "10000");

        //初始化acsClient,暂不支持region化
        IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
        IAcsClient acsClient = new DefaultAcsClient(profile);

        //组装请求对象-具体描述见控制台-文档部分内容
        SendSmsRequest request = new SendSmsRequest();
        //必填:待发送手机号
        request.setPhoneNumbers(phone);
        //必填:短信签名-可在短信控制台中找到
        request.setSignName("天成商城");
        //必填:短信模板-可在短信控制台中找到
        request.setTemplateCode(templateCode);
        //可选:模板中的变量替换JSON串,如模板内容为"亲爱的${name},您的验证码为${code}"时,此处的值为
        request.setTemplateParam("{\"code\":\""+number+"\"}");
        //选填-上行短信扩展码(无特殊需求用户请忽略此字段)
        //request.setSmsUpExtendCode("90997");
        //可选:outId为提供给业务方扩展字段,最终在短信回执消息中将此值带回给调用者
        request.setOutId("yourOutId");
        //hint 此处可能会抛出异常，注意catch
        SendSmsResponse sendSmsResponse = acsClient.getAcsResponse(request);
        return sendSmsResponse;
    }

}