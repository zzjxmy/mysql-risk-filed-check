package com.fieldcheck.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldcheck.entity.AlertConfig;
import com.fieldcheck.entity.AlertType;
import com.fieldcheck.entity.TaskExecution;
import com.fieldcheck.repository.AlertConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertConfigRepository alertConfigRepository;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    public List<AlertConfig> getAllConfigs() {
        return alertConfigRepository.findAll();
    }

    public List<AlertConfig> getEnabledConfigs() {
        return alertConfigRepository.findByEnabled(true);
    }

    public AlertConfig getConfig(Long id) {
        return alertConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("告警配置不存在"));
    }

    @Transactional
    public AlertConfig createConfig(AlertConfig config) {
        if (alertConfigRepository.existsByName(config.getName())) {
            throw new RuntimeException("配置名称已存在");
        }
        return alertConfigRepository.save(config);
    }

    @Transactional
    public AlertConfig updateConfig(Long id, AlertConfig configData) {
        AlertConfig config = getConfig(id);
        config.setName(configData.getName());
        config.setAlertType(configData.getAlertType());
        config.setConfig(configData.getConfig());
        config.setEnabled(configData.getEnabled());
        config.setRemark(configData.getRemark());
        return alertConfigRepository.save(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        alertConfigRepository.deleteById(id);
    }

    public void testAlert(Long id) {
        AlertConfig config = getConfig(id);
        if (!config.getEnabled()) {
            throw new RuntimeException("告警配置未启用");
        }
        
        String testMessage = "## MySQL字段容量检查测试消息\n\n" +
                "**配置名称**: " + config.getName() + "\n" +
                "**告警类型**: " + config.getAlertType() + "\n" +
                "**测试时间**: " + java.time.LocalDateTime.now() + "\n\n" +
                "这是一条测试消息，用于验证告警配置是否正确。";
        
        try {
            if (config.getAlertType() == AlertType.DINGTALK) {
                sendDingTalkAlert(config, testMessage);
            } else if (config.getAlertType() == AlertType.EMAIL) {
                sendTestEmailAlert(config, testMessage);
            }
            log.info("Test alert sent successfully for config: {}", config.getName());
        } catch (Exception e) {
            log.error("Failed to send test alert: {}", e.getMessage());
            throw new RuntimeException("发送测试消息失败: " + e.getMessage());
        }
    }

    public void sendAlert(TaskExecution execution, List<AlertConfig> configs) {
        String message = buildAlertMessage(execution);
        
        for (AlertConfig config : configs) {
            if (!config.getEnabled()) continue;
            
            try {
                if (config.getAlertType() == AlertType.DINGTALK) {
                    sendDingTalkAlert(config, message);
                } else if (config.getAlertType() == AlertType.EMAIL) {
                    sendEmailAlert(config, execution, message);
                }
            } catch (Exception e) {
                log.error("Failed to send alert via {}: {}", config.getName(), e.getMessage());
            }
        }
    }

    private String buildAlertMessage(TaskExecution execution) {
        StringBuilder sb = new StringBuilder();
        sb.append("## MySQL字段容量检查报告\n\n");
        sb.append("**任务名称**: ").append(execution.getTask().getName()).append("\n");
        sb.append("**执行状态**: ").append(execution.getStatus().name()).append("\n");
        sb.append("**检查表数**: ").append(execution.getTotalTables()).append("\n");
        sb.append("**发现风险**: ").append(execution.getRiskCount()).append("\n");
        sb.append("**开始时间**: ").append(execution.getStartTime()).append("\n");
        sb.append("**结束时间**: ").append(execution.getEndTime()).append("\n");
        
        if (execution.getRiskCount() > 0) {
            sb.append("\n**请及时查看详情并处理风险！**");
        }
        
        return sb.toString();
    }

    private void sendDingTalkAlert(AlertConfig config, String message) throws Exception {
        JsonNode configJson = objectMapper.readTree(config.getConfig());
        String webhook = configJson.get("webhook").asText();
        String secret = configJson.has("secret") ? configJson.get("secret").asText() : null;

        // Build signed URL if secret is provided
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.encodeBase64String(signData), "UTF-8");
            webhook = webhook + "&timestamp=" + timestamp + "&sign=" + sign;
        }

        // Build message body
        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("msgtype", "markdown");
            put("markdown", new java.util.HashMap<String, String>() {{
                put("title", "MySQL字段容量检查报告");
                put("text", message);
            }});
        }});

        // Send request
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(webhook);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
            
            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("DingTalk API returned " + statusCode);
                }
            }
        }
        
        log.info("DingTalk alert sent successfully");
    }

    private void sendEmailAlert(AlertConfig config, TaskExecution execution, String message) throws Exception {
        JsonNode configJson = objectMapper.readTree(config.getConfig());
        String recipients = configJson.get("emailRecipients") != null ? 
                configJson.get("emailRecipients").asText() : 
                configJson.get("recipients").asText();

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(recipients.split(","));
        mailMessage.setSubject("MySQL字段容量检查报告 - " + execution.getTask().getName());
        mailMessage.setText(message.replace("**", "").replace("##", ""));

        mailSender.send(mailMessage);
        log.info("Email alert sent successfully to {}", recipients);
    }

    private void sendTestEmailAlert(AlertConfig config, String message) throws Exception {
        JsonNode configJson = objectMapper.readTree(config.getConfig());
        String recipients = configJson.get("emailRecipients") != null ? 
                configJson.get("emailRecipients").asText() : 
                configJson.get("recipients").asText();

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(recipients.split(","));
        mailMessage.setSubject("MySQL字段容量检查测试消息");
        mailMessage.setText(message.replace("**", "").replace("##", ""));

        mailSender.send(mailMessage);
        log.info("Test email alert sent successfully to {}", recipients);
    }
}
