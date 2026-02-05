# Autonomous Insurance Claims Processing Agent

## Project Overview
This project implements a lightweight insurance claims processing agent that handles First Notice of Loss (FNOL) documents. The goal of the system is to automatically extract key claim details, validate required information, and route the claim to the appropriate workflow based on predefined business rules.

The solution is designed to be simple, readable, and maintainable, focusing on core backend logic rather than complex AI or document parsing techniques.

## Problem Statement
Insurance FNOL documents often arrive in semi-structured formats (such as TXT or PDF) and may contain missing, incomplete, or suspicious information. Manually reviewing every claim is time-consuming and inefficient.

This project addresses that problem by:
*   Converting FNOL documents into structured data
*   Identifying missing or critical fields
*   Automatically deciding how the claim should be processed
*   Clearly explaining why a particular decision was made

## How the System Works
The processing flow follows four clear steps:

### 1. FNOL Input
The system accepts FNOL documents in text format (representative of real FNOL content). Sample FNOL files are included to demonstrate different claim scenarios such as:
*   Fast-track claims
*   Injury claims
*   Fraud-suspected claims
*   Claims requiring manual review

### 2. Field Extraction
The processor scans the FNOL document using regular expressions and extracts structured information, including:
*   **Policy information** (policy number, policyholder name, effective date)
*   **Incident details** (date, time, location, description)
*   **Involved parties** (driver/claimant)
*   **Asset details** (vehicle information and estimated damage)
*   **Mandatory claim attributes** (claim type)

If a field cannot be found, it is recorded as `null`.

### 3. Validation & Missing Field Detection
Certain fields are treated as mandatory for automated processing. If any mandatory field is missing or marked as `Unknown`, the claim is flagged accordingly. This step ensures the system behaves realistically when encountering incomplete or low-quality FNOL data.

### 4. Claim Routing Logic
Based on the extracted data, the claim is routed using simple and transparent business rules:

*   **Fast-track**: Estimated damage is below ₹25,000 and no mandatory fields are missing.
*   **Specialist Queue**: Claim involves injury.
*   **Investigation Flag**: Accident description contains suspicious indicators such as fraud, staged, or inconsistent.
*   **Manual Review**: Mandatory fields are missing or the damage amount exceeds the fast-track threshold.

Each routing decision includes a human-readable explanation describing why the claim was routed that way.

## Output Format
The final output is a structured JSON object containing:

{
  "extractedFields": { ... },
  "missingFields": [ ... ],
  "recommendedRoute": "Fast-track | Manual Review | Investigation Flag | Specialist Queue",
  "reasoning": "Explanation for the routing decision"
}

This format ensures transparency and makes the system easy to integrate with downstream workflows.

## Design Decisions
*   **Java** was chosen to demonstrate strong backend fundamentals, clear object-oriented design, and readable business logic.
*   The solution is **rule-based**, as the assessment focuses on reasoning and decision-making rather than machine learning.
*   The system is intentionally kept **lightweight**, avoiding frameworks or unnecessary complexity.
*   Sample FNOL documents are included only for testing and demonstration; the processor works with any similar FNOL input.

## How to Run

1.  **Compile the code:**

    javac FNOLProcessor.java

2.  **Run the processor with a sample FNOL file:**
 
    java FNOLProcessor sample_number.txt

## Conclusion
This project demonstrates how a simple backend service can:
*   Process real-world insurance documents
*   Handle incomplete or inconsistent data
*   Apply business rules reliably
*   Produce clear, explainable decisions

The focus is on clarity, correctness, and maintainability, which are essential qualities for a junior software developer working on backend systems.
