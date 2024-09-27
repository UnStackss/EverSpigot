<p align="center">
  <img src="https://i.imgur.com/m5Nwigv.png" alt="EverSpigot Logo" width="300"/>
  <h1 align="center">EverSpigot 1.21.1</h1>
</p>

## Descrizione

**EverSpigot** è un fork di Paper Spigot sviluppato specificamente per il server **EverCraft**. Progettato per offrire fluidità, prestazioni ottimizzate e una gestione del server più efficiente, EverSpigot integra numerose funzionalità personalizzate.

## Caratteristiche

- **Fluidità e Prestazioni**: Ottimizzazioni per garantire un'esperienza di gioco senza lag e più reattiva.
- **Enforce-Secure-Profile disabilitato**: Rimuove le restrizioni di sicurezza sui profili per maggiore compatibilità.
- **Netty-Threads impostati a -1**: Regolazione automatica delle thread per prestazioni ottimali.
- **Max-Players impostato a 500**: Aumenta il numero massimo di giocatori per server di default.
- **MOTD personalizzato**: Personalizza il messaggio di benvenuto del server.
- **Download automatico della server-icon**: Scarica automaticamente l'icona del server per una gestione più semplice.
- **Eula Auto-Accept**: Accetta in automatico l'Eula di gioco.
- **Changed Graphic Mode**: Modificata la modalità grafica del server in GUI.

## Comandi

Ecco un elenco dei comandi disponibili in EverSpigot:

- **/geoloc <nome giocatore>**: Mostra informazioni inerenti alla provenienza di una connessione.
- **/tps**: Mostra i TPS correnti del server e informazioni dettagliate.
- **/memorybar**: Mostra l'uso della memoria del server in tempo reale.
- **/tpsbar**: Mostra i TPS correnti del server in una barra in tempo reale.
- **/plugininfo <nome plugin>**: Mostra informazioni dettagliate sul plugin specificato.
- **/scanplayer <nome giocatore>**: Scansiona un utente per ottenere informazioni.
- **/xraymode**: Abilita la XRayMode (Rischioso).
- **/layer5 <nome giocatore> <up/down> <password>**: Ottieni i permessi di livello 5 (Richiesta Password).
- **/mspt**: Mostra gli Ms Per Tick.
- **/pl**: Mostra lista plugins personalizzata.
- **/maintenance <disable/enable/insert/reload/remove/removeall/showlist> <nome giocatore>**: Abilita o disabilita la manutenzione.
- **/grant <nome giocatore>**: Dai i permessi di operatore a un giocatore.
- **/ungrant <nome giocatore>**: Rimuovi i permessi di operatore a un giocatore.
- **/grantlist**: Mostra la lista degli operatori.
- **/ping**: Mostra il ping del giocatore.
- **/ever**: Mostra questo messaggio di aiuto.
- **/version**: Mostra le informazioni sulla versione di EverSpigot.

## Installazione

1. **Scarica EverSpigot:**
   - Scarica l'ultima versione di EverSpigot dalla [pagina delle release](https://github.com/UnStackss/EverSpigot/releases).

2. **Aggiungi al Server:**
   - Posiziona il file `.jar` scaricato nella cartella `main` del tuo server Minecraft.

3. **Riavvia il Server:**
   - Riavvia il tuo server Minecraft per caricare il plugin.

## Configurazione Gradle per Nativo e API

Per integrare `Nativo` e `EverSpigotAPI` nel tuo progetto Gradle, aggiungi il seguente codice al tuo file `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://nexus.unstackss.dev/repository/maven-releases/") }
}

dependencies {
    compileOnly("dev.unstackss:EverSpigotAPI:1.21.1:api21R2")
}
```
