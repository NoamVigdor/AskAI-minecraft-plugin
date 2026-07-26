# Privacy Policy

Last updated: 2026-07-26

## Overview

AskAI is a server plugin that lets players send prompts to an AI provider chosen by the server owner. This policy explains what data may be processed by the plugin and by third-party AI services.

## Data the Plugin Processes

The plugin may process:

- player prompts sent through `/Ask` or `/AI`
- short in-memory conversation history used to improve follow-up replies
- selected provider and model settings
- provider API key presence state for admin status checks

The plugin does not intentionally collect passwords, payment data, or account recovery data. Players and admins should never submit that information through the plugin.

## Where Data Goes

When a player uses the plugin, prompt data is sent to the currently configured AI provider. Depending on server configuration, that provider may be:

- a third-party hosted AI service
- a local model provider such as Ollama

If a hosted provider is used, player prompts leave the Minecraft server and are processed by that provider under its own privacy and retention policies.

## Local Storage

The plugin may store:

- provider configuration in `config.yml`
- provider API keys from server-side setup in `secrets.yml`

Conversation history is kept in memory only and is not designed to persist across server restarts.

## Access and Security

- API keys cannot be set in Minecraft chat
- API keys are intended to be stored in server-side `secrets.yml`
- remote providers must use HTTPS unless the provider endpoint is local
- the plugin includes rate limits and output safety filtering

## Server Owner Responsibility

Server owners are responsible for:

- choosing which provider to use
- configuring permissions
- securing access to the server, host panel, backups, and plugin files
- informing players that prompts may be processed by a third-party AI provider

## Player Guidance

Players should not submit:

- passwords
- API keys
- one-time codes
- recovery codes
- payment card details
- bank information
- personally sensitive account data

## Third-Party Services

Hosted AI providers have their own terms and privacy policies. Server owners should review the policies for each provider they enable.

## Changes

This policy may be updated as the plugin changes. Server owners distributing this plugin should review and adapt this policy to match their own deployment and legal needs.
