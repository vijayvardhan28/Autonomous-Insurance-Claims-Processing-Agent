import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FNOLProcessor {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java FNOLProcessor <path_to_fnol.txt>");
            return;
        }

        String text = Files.readString(Paths.get(args[0]));
        Map<String, Object> extracted = extractFields(text);
        Map<String, Object> result = routeClaim(extracted);

        System.out.println(toJson(result));
    }

    // ===================== FEILDS =====================
    static Map<String, Object> extractFields(String text) {
        Map<String, Object> data = new LinkedHashMap<>();

        // 1. Policy Info
        Map<String, Object> policyInfo = new LinkedHashMap<>();
        policyInfo.put("policy_number", match(text, "POLICY NUMBER:\\s*([A-Za-z0-9-]+)"));
        policyInfo.put("policyholder_name", match(text, "NAME OF INSURED:\\s*(.+)"));
        policyInfo.put("effective_dates", match(text, "EFFECTIVE DATE:\\s*(\\d{2}/\\d{2}/\\d{4})"));
        data.put("policy_info", policyInfo);

        // 2. Incident Info
        Map<String, Object> incidentInfo = new LinkedHashMap<>();
        incidentInfo.put("date", match(text, "DATE OF LOSS:\\s*(\\d{2}/\\d{2}/\\d{4})"));
        incidentInfo.put("time", match(text, "TIME:\\s*(\\d{1,2}:\\d{2}\\s?(?:AM|PM)?)"));
        incidentInfo.put("location", match(text, "LOCATION OF LOSS:\\s*(.+)"));

        String desc = match(text, "DESCRIPTION OF ACCIDENT:\\s*([\\s\\S]*?)(?=\\R+[A-Z])");
        incidentInfo.put("description", desc == null ? null : desc.replaceAll("\\s+", " ").trim());

        data.put("incident_info", incidentInfo);

        // 3. Involved Parties
        Map<String, Object> involvedParties = new LinkedHashMap<>();
        involvedParties.put("claimant", match(text, "DRIVER'S NAME:\\s*(.+)")); // Assuming simple case
        involvedParties.put("third_parties", new ArrayList<>());
        involvedParties.put("contact_details", null);
        data.put("involved_parties", involvedParties);

        // 4. Asset Details
        Map<String, Object> assetDetails = new LinkedHashMap<>();
        String vin = match(text, "V\\.I\\.N\\.:\\s*([A-Za-z0-9]+)");
        assetDetails.put("asset_type", vin != null ? "Vehicle" : "Unknown");
        assetDetails.put("asset_id_vin", vin);
        assetDetails.put("make", match(text, "MAKE:\\s*([A-Za-z0-9]+)"));
        assetDetails.put("model", match(text, "MODEL:\\s*([A-Za-z0-9-]+)"));
        assetDetails.put("year", match(text, "YEAR:\\s*(\\d{4})"));

        String estimate = match(text, "ESTIMATE AMOUNT:\\s*\\$?([\\d,]+)");
        assetDetails.put("estimated_damage",
                estimate == null ? null : Double.parseDouble(estimate.replace(",", "")));
        data.put("asset_details", assetDetails);

        // 5. Mandatory Others
        Map<String, Object> mandatoryOthers = new LinkedHashMap<>();
        String claimType = "Unknown";
        if (text.toUpperCase().contains("INJURY"))
            claimType = "Injury";
        else if (text.toUpperCase().contains("COLLISION") || text.toUpperCase().contains("DAMAGE"))
            claimType = "Property Damage";
        mandatoryOthers.put("claim_type", claimType);
        mandatoryOthers.put("attachments", new ArrayList<>());
        mandatoryOthers.put("initial_estimate", null);
        data.put("mandatory_others", mandatoryOthers);

        return data;
    }

    static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    // ===================== ROUTING =====================
    static Map<String, Object> routeClaim(Map<String, Object> data) {
        List<String> missing = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> policyInfo = (Map<String, Object>) data.get("policy_info");
        @SuppressWarnings("unchecked")
        Map<String, Object> incidentInfo = (Map<String, Object>) data.get("incident_info");
        @SuppressWarnings("unchecked")
        Map<String, Object> mandatoryOthers = (Map<String, Object>) data.get("mandatory_others");
        @SuppressWarnings("unchecked")
        Map<String, Object> assetDetails = (Map<String, Object>) data.get("asset_details");

        if (policyInfo.get("policy_number") == null)
            missing.add("policy_number");
        if (incidentInfo.get("date") == null)
            missing.add("date");
        if (incidentInfo.get("location") == null)
            missing.add("location");
        if (mandatoryOthers.get("claim_type").equals("Unknown"))
            missing.add("claim_type");
        if (incidentInfo.get("description") == null)
            missing.add("description");

        @SuppressWarnings("unchecked")
        Map<String, Object> involvedParties = (Map<String, Object>) data.get("involved_parties");
        if (involvedParties.get("claimant") == null)
            missing.add("claimant");

        if (assetDetails.get("asset_id_vin") == null)
            missing.add("vin");

        String route = "Manual Review";
        String reason;

        if (!missing.isEmpty()) {
            reason = "Missing mandatory fields: " + String.join(", ", missing);
        } else {
            String desc = ((String) incidentInfo.get("description")).toLowerCase();

            if (desc.contains("fraud") || desc.contains("staged") || desc.contains("inconsistent")) {
                route = "Investigation Flag";
                reason = "Suspicious keywords found in description.";
            } else if (mandatoryOthers.get("claim_type").equals("Injury")) {
                route = "Specialist Queue";
                reason = "Claim involves injury.";
            } else {
                Double damage = (Double) assetDetails.get("estimated_damage");
                if (damage != null && damage < 25000) {
                    route = "Fast-track";
                    reason = "Estimated damage under $25,000.";
                } else {
                    reason = "High or missing damage estimate.";
                }
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("extractedFields", data);
        output.put("missingFields", missing);
        output.put("recommendedRoute", route);
        output.put("reasoning", reason);
        return output;
    }

    // ===================== OUTPUT JSON =====================
    static String toJson(Object obj) {
        return toJson(obj, 0);
    }

    static String toJson(Object obj, int indent) {
        String spaces = " ".repeat(indent);
        String childSpaces = " ".repeat(indent + 2);

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.isEmpty())
                return "{}";

            StringBuilder sb = new StringBuilder("{\n");
            int count = 0;
            for (var e : map.entrySet()) {
                sb.append(childSpaces)
                        .append("\"").append(e.getKey()).append("\": ")
                        .append(toJson(e.getValue(), indent + 2));

                if (++count < map.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(spaces).append("}");
            return sb.toString();
        }

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty())
                return "[]";

            StringBuilder sb = new StringBuilder("[\n");
            int count = 0;
            for (Object o : list) {
                sb.append(childSpaces)
                        .append(toJson(o, indent + 2));

                if (++count < list.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(spaces).append("]");
            return sb.toString();
        }

        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }

        return String.valueOf(obj);
    }

    static String escape(String s) {
        if (s == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
