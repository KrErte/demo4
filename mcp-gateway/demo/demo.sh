#!/bin/bash
# MCP Gateway Interactive Demo
# Näitab kuidas AI ja Gateway suhtlevad

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

clear
echo "═══════════════════════════════════════════════════════════════"
echo "           🤖 MCP Gateway Demo - AI Turvavärav"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Demo 1: Lubatud fail
echo -e "${BLUE}━━━ Stsenaarium 1: AI tahab lugeda lubatud faili ━━━${NC}"
echo ""
echo -e "${YELLOW}[AI küsib]:${NC} \"Palun loe faili /tmp/notes.txt\""
sleep 1
echo -e "${GREEN}[Gateway]:${NC} ✓ /tmp on lubatud kaustas"
echo -e "${GREEN}[Gateway]:${NC} ✓ Poliitika lubab fs.readFile"
echo -e "${GREEN}[Gateway]:${NC} ✓ LUBATUD - tagastan faili sisu"
echo ""
echo -e "${GREEN}[Audit log]:${NC} {\"tool\":\"fs.readFile\",\"decision\":\"ALLOW\",\"path\":\"/tmp/notes.txt\"}"
echo ""

sleep 2

# Demo 2: Keelatud fail
echo -e "${BLUE}━━━ Stsenaarium 2: AI tahab lugeda tundlikku faili ━━━${NC}"
echo ""
echo -e "${YELLOW}[AI küsib]:${NC} \"Palun loe faili /etc/passwd\""
sleep 1
echo -e "${RED}[Gateway]:${NC} ✗ /etc EI OLE lubatud kaustade nimekirjas"
echo -e "${RED}[Gateway]:${NC} ✗ KEELATUD - ligipääs blokeeritud"
echo ""
echo -e "${RED}[Audit log]:${NC} {\"tool\":\"fs.readFile\",\"decision\":\"DENY\",\"reason\":\"Path not in allowlist\"}"
echo ""

sleep 2

# Demo 3: SQL injection katse
echo -e "${BLUE}━━━ Stsenaarium 3: AI proovib ohtlikku SQL päringut ━━━${NC}"
echo ""
echo -e "${YELLOW}[AI küsib]:${NC} \"Käivita: DROP TABLE users;\""
sleep 1
echo -e "${RED}[Gateway]:${NC} ✗ Tuvastatud keelatud käsk: DROP"
echo -e "${RED}[Gateway]:${NC} ✗ Ainult SELECT päringud on lubatud"
echo -e "${RED}[Gateway]:${NC} ✗ KEELATUD - ohtlik päring blokeeritud"
echo ""
echo -e "${RED}[Audit log]:${NC} {\"tool\":\"db.query\",\"decision\":\"DENY\",\"reason\":\"Blocked keyword: DROP\"}"
echo ""

sleep 2

# Demo 4: Lubatud veebipäring
echo -e "${BLUE}━━━ Stsenaarium 4: AI tahab pärida lubatud API-t ━━━${NC}"
echo ""
echo -e "${YELLOW}[AI küsib]:${NC} \"Too andmed api.github.com/users/octocat\""
sleep 1
echo -e "${GREEN}[Gateway]:${NC} ✓ github.com on lubatud domeenide nimekirjas"
echo -e "${GREEN}[Gateway]:${NC} ✓ Ainult GET päring (ohutu)"
echo -e "${GREEN}[Gateway]:${NC} ✓ LUBATUD - tagastan API vastuse"
echo ""
echo -e "${GREEN}[Audit log]:${NC} {\"tool\":\"web.fetch\",\"decision\":\"ALLOW\",\"domain\":\"api.github.com\"}"
echo ""

sleep 2

# Demo 5: Keelatud domeen
echo -e "${BLUE}━━━ Stsenaarium 5: AI tahab pärida tundmatut saiti ━━━${NC}"
echo ""
echo -e "${YELLOW}[AI küsib]:${NC} \"Too andmed evil-site.com/steal-data\""
sleep 1
echo -e "${RED}[Gateway]:${NC} ✗ evil-site.com EI OLE lubatud domeenide nimekirjas"
echo -e "${RED}[Gateway]:${NC} ✗ KEELATUD - võrguligipääs blokeeritud"
echo ""
echo -e "${RED}[Audit log]:${NC} {\"tool\":\"web.fetch\",\"decision\":\"DENY\",\"reason\":\"Domain not in allowlist\"}"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo -e "${GREEN}                    Demo lõpetatud!${NC}"
echo ""
echo "  Kokkuvõte: MCP Gateway tagab, et AI saab teha AINULT seda,"
echo "  mis on selgelt lubatud - kõik muu blokeeritakse ja logitakse."
echo ""
echo "═══════════════════════════════════════════════════════════════"
