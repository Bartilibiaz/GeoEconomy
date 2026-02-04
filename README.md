<h1 align="center">
  <br>
  🌍 GeoEconomy
  <br>
</h1>

<h4 align="center">Advanced Dynamic Market System for Minecraft Servers.</h4>

<p align="center">
  <a href="#key-features">Key Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#commands">Commands</a> •
  <a href="#web-interface">Web Interface</a> •
  <a href="#discord-integration">Discord</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/API-Spigot%20%2F%20Paper-orange?style=flat-square" alt="API">
  <img src="https://img.shields.io/badge/Java-17%2B-red?style=flat-square" alt="Java">
</p>

---

## 📝 Overview

**GeoEconomy** brings real-world economic mechanics to your Minecraft server. Unlike standard shop plugins, prices in GeoEconomy are **dynamic** — they fluctuate based on player supply and demand.

The plugin features a robust **Investment Portfolio**, allowing players to store assets and track their value over time. It also includes a built-in **HTTP Web Server** to display price history charts in a browser and **Discord Integration** for account linking.

## ✨ Key Features

* **📈 Dynamic Pricing:** Prices rise when players buy and drop when they sell.
* **🖥️ Modern GUI:** Clean, intuitive interface for trading and browsing categories.
* **💼 Investment Portfolio:** Players can deposit items into a virtual wallet and track their total asset value (Unrealized PnL).
* **📊 Web Dashboard:** Built-in web server hosting real-time price history graphs (Chart.js).
* **🤖 Discord Integration:** Secure account linking system via verification codes.
* **💾 SQLite Storage:** Fast, local database storage for player profiles and history (no MySQL setup required).
* **🌍 Multi-Language:** Full support for English (`en`) and Polish (`pl`) via configuration.
* **📉 Trend Analysis:** View price changes over the last 1 hour or 24 hours directly in the GUI.

## 📥 Installation

1.  Download the latest `GeoEconomy-1.0-SNAPSHOT.jar`.
2.  Ensure you have **Vault** and an economy plugin (e.g., EssentialsX) installed.
3.  Drop the jar into your `plugins` folder.
4.  Restart the server.
5.  Configure `config.yml`, `discord.yml` (optional), and `market.yml`.

## ⚙️ Configuration & Web Interface

GeoEconomy hosts a lightweight web server to display market data.

1.  Open `config.yml` and set the port (default: `8081`).
2.  Ensure this port is open on your firewall/hosting.
3.  Access the dashboard via: `http://your-server-ip:8081`

To change the language to English, set in `config.yml`:
```yaml
settings:
  language: en
