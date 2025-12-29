@echo off
chcp 65001 >nul
cls

echo ═══════════════════════════════════════════════════════════════
echo            MCP Gateway Demo - AI Turvavärav
echo ═══════════════════════════════════════════════════════════════
echo.

echo --- Stsenaarium 1: AI tahab lugeda lubatud faili ---
echo.
echo [AI küsib]: "Palun loe faili /tmp/notes.txt"
timeout /t 1 >nul
echo [Gateway]: [OK] /tmp on lubatud kaustas
echo [Gateway]: [OK] Poliitika lubab fs.readFile
echo [Gateway]: [OK] LUBATUD - tagastan faili sisu
echo.
echo [Audit log]: {"tool":"fs.readFile","decision":"ALLOW","path":"/tmp/notes.txt"}
echo.

timeout /t 2 >nul

echo --- Stsenaarium 2: AI tahab lugeda tundlikku faili ---
echo.
echo [AI küsib]: "Palun loe faili /etc/passwd"
timeout /t 1 >nul
echo [Gateway]: [X] /etc EI OLE lubatud kaustade nimekirjas
echo [Gateway]: [X] KEELATUD - ligipääs blokeeritud
echo.
echo [Audit log]: {"tool":"fs.readFile","decision":"DENY","reason":"Path not in allowlist"}
echo.

timeout /t 2 >nul

echo --- Stsenaarium 3: AI proovib ohtlikku SQL päringut ---
echo.
echo [AI küsib]: "Käivita: DROP TABLE users;"
timeout /t 1 >nul
echo [Gateway]: [X] Tuvastatud keelatud käsk: DROP
echo [Gateway]: [X] Ainult SELECT päringud on lubatud
echo [Gateway]: [X] KEELATUD - ohtlik päring blokeeritud
echo.
echo [Audit log]: {"tool":"db.query","decision":"DENY","reason":"Blocked keyword: DROP"}
echo.

timeout /t 2 >nul

echo --- Stsenaarium 4: AI tahab pärida lubatud API-t ---
echo.
echo [AI küsib]: "Too andmed api.github.com/users/octocat"
timeout /t 1 >nul
echo [Gateway]: [OK] github.com on lubatud domeenide nimekirjas
echo [Gateway]: [OK] Ainult GET päring (ohutu)
echo [Gateway]: [OK] LUBATUD - tagastan API vastuse
echo.
echo [Audit log]: {"tool":"web.fetch","decision":"ALLOW","domain":"api.github.com"}
echo.

timeout /t 2 >nul

echo --- Stsenaarium 5: AI tahab pärida tundmatut saiti ---
echo.
echo [AI küsib]: "Too andmed evil-site.com/steal-data"
timeout /t 1 >nul
echo [Gateway]: [X] evil-site.com EI OLE lubatud domeenide nimekirjas
echo [Gateway]: [X] KEELATUD - võrguligipääs blokeeritud
echo.
echo [Audit log]: {"tool":"web.fetch","decision":"DENY","reason":"Domain not in allowlist"}
echo.

echo ═══════════════════════════════════════════════════════════════
echo                     Demo lõpetatud!
echo.
echo   Kokkuvõte: MCP Gateway tagab, et AI saab teha AINULT seda,
echo   mis on selgelt lubatud - kõik muu blokeeritakse ja logitakse.
echo.
echo ═══════════════════════════════════════════════════════════════
pause
