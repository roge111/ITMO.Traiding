package main

import "testing"

func TestParseQuoteLine(t *testing.T) {
	quote, err := parseQuoteLine("SBER,12345,2026-06-23 12:30:00")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if quote.name != "SBER" || quote.price != 12345 {
		t.Fatalf("unexpected quote: %+v", quote)
	}
}

func TestParseQuoteLineRejectsInvalidData(t *testing.T) {
	cases := []string{
		"",
		"SBER",
		"SBER,-1,2026-06-23 12:30:00",
		"SBER,100,wrong-time",
	}
	for _, line := range cases {
		if _, err := parseQuoteLine(line); err == nil {
			t.Errorf("expected error for %q", line)
		}
	}
}
