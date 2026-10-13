# Tapis Control (v0.1)

App Android (Kotlin + Jetpack Compose) per comandare un tapis roulant FTMS via Bluetooth LE
(testata sulla carta per Lotto Pace G600: espone il servizio Fitness Machine 0x1826).

## Come aprirla
1. Android Studio (Ladybug o piu' recente) -> File > Open -> cartella `TapisControl`.
2. Attendi il Gradle sync (scarica Gradle 8.9 e le dipendenze).
3. Collega il telefono (debug USB attivo) e premi Run.

## Uso
- Scheda Connessione: "Cerca tapis roulant", tocca il dispositivo, attendi "Pronto".
  Il log mostra le caratteristiche trovate e le risposte del tapis roulant a ogni comando.
- Scheda Manuale: Start/Stop, velocita' e inclinazione con +/-, limite massimo di velocita'.
- Scheda Allenamento: lista di step (minuti, km/h, %), Avvia/Pausa/Stop.
  Tra uno step e l'altro la variazione e' graduale (6 s); a fine allenamento defaticamento a 2 km/h e stop.

## Note
- Lo schermo resta acceso durante l'uso: in questa versione non c'e' un servizio in background.
- Se il tapis roulant risponde "non supportato" a velocita'/inclinazione, controlla il log.
- Tieni sempre la chiave di sicurezza del tapis roulant a portata di mano.

## Compilare l'APK senza Android Studio (GitHub Actions)
1. Crea un account su github.com e un nuovo repository privato.
2. Carica tutto il contenuto della cartella `TapisControl` (compresa la cartella nascosta `.github`).
3. Vai nella scheda Actions: parte da solo il workflow "Build APK" (oppure Run workflow).
4. A fine build (circa 5-10 minuti) apri l'esecuzione e scarica l'artefatto `TapisControl-apk`: dentro c'e' `app-debug.apk`.
5. Sul telefono: copia l'APK, aprilo e consenti l'installazione da origini sconosciute.
