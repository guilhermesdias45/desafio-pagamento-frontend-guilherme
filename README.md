# Development Environment Setup

## Overview

This directory contains a comprehensive development environment setup script (`setup-dev.sh`) that automates the process of setting up and managing local development services for the frontend application.

## Key Features

### 🚀 **Service Management**
- **Start Services**: Automatically starts backend (Docker Compose) and frontend (Vite dev server)
- **Restart Services**: Restarts all services with fresh state
- **Status Check**: Detailed service status reporting
- **Stop Services**: Clean shutdown of all running services

### ⚡ **Ngrok Integration**
- **Tunnel Setup**: Automatic ngrok tunnel creation for external access
- **URL Management**: Automatic extraction of ngrok URLs
- **Authentication**: Secure ngrok authentication management
- **Environment Variables**: Updates `.env` file with external URLs

### 🛠️ **Development Tools**
- **Dependency Installation**: npm dependency management
- **Docker Management**: Docker Compose service orchestration
- **Port Management**: Automatic port conflict detection and management
- **Process Management**: PID tracking and clean process termination

### 📊 **Logging & Monitoring**
- **Verbose Logging**: Detailed output for troubleshooting
- **Service Status**: Real-time service health checking
- **Error Handling**: Comprehensive error messages and recovery
- **Debug Support**: Detailed debugging information

## Quick Start

### 1. Clone the Repository
```bash
git clone [repository-url]
cd desafio-pagamento-frontend-guilherme
```

### 2. Make the Setup Script Executable
```bash
chmod +x setup-dev.sh
```

### 3. Initial Setup (Run Once)
```bash
./setup-dev.sh setup
```

### 4. Start Development Environment
```bash
./setup-dev.sh start
```

### 5. Check Service Status
```bash
./setup-dev.sh status
```

### 6. Restart Services
```bash
./setup-dev.sh restart
```

### 7. Stop Services
```bash
./setup-dev.sh stop
```

## Directory Structure

```
/desafio-pagamento-frontend-guilherme/
├── setup-dev.sh                    # Development environment setup script
├── package.json                    # Node.js project configuration
├── docker-compose.yml              # Docker services configuration (if exists)
├── .env                           # Environment variables
├── docs/
│   ├── screenshots/              # Application screenshots
│   └── design-plan.md             # Design system documentation
├── src/
│   ├── lib/                      # Library source code
│   │   ├── design-tokens.ts        # Design tokens
│   │   ├── palette.ts            # Legacy color palette
│   │   └── jwt.ts                # JWT utilities
│   ├── components/               # UI components
│   │   ├── ui/                   # Component library
│   │   ├── layout/               # Layout components
│   └── pages/                    # Application pages
├── node_modules/                 # Node.js dependencies
└── *.log                        # Log files
```

## Usage Examples

### Start Development Environment
```bash
# Start all services (frontend + backend)
./setup-dev.sh start
```

### Restart with Fresh State
```bash
# Stop, clean up, and restart all services
./setup-dev.sh restart
```

### Check Service Status
```bash
./setup-dev.sh status
```

### Initial Environment Setup
```bash
./setup-dev.sh setup
```

### Stop All Services
```bash
./setup-dev.sh stop
```

## Features

### 🔧 **Automated Service Management**
- Automatic dependency installation
- Docker Compose service orchestration
- Frontend and backend start/stop management
- Process PID tracking and clean shutdown

### ⚡ **Ngrok Integration**
- Automatic ngrok tunnel creation
- Authentication token management
- External URL extraction and updates
- Development environment configuration

### 📊 **Monitoring & Debugging**
- Detailed logging with different levels
- Service status checking
- Port availability verification
- Error recovery and troubleshooting

### 🔐 **Environment Management**
- Automated `.env` file creation
- Environment variable management
- Secure authentication handling
- Configuration persistence

### 🛡️ **Development Tools**
- Cross-platform compatibility
- Verbose mode for detailed output
- Error handling and recovery
- Process management utilities

## Requirements

### Prerequisites
- [x] **Linux/macOS** (Windows with WSL or Git Bash)
- [x] **Node.js 18+** (for frontend)
- [x] **Docker** (for backend services)
- [x] **Git** (version control)

### Optional Tools
- [x] **ngrok** (for external tunnel setup)
- [x] **curl/wget** (for API testing)
- [x **jq** (for JSON parsing in shell scripts)

### Installation

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install curl wget git docker.io docker-compose jq
```

#### macOS
```bash
# Install Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install dependencies
hom Brew install node docker-compose jq
curl --version || brew install curl
```

#### Windows
```powershell
# Install WSL2 if not already installed
wsl --install

# Inside WSL or Ubuntu Subsystem
apt update
apt install nodejs npm docker.io docker-compose jq
```

## Contribution

### Development
```bash
# Clone this repository
cd desafio-pagamento-frontend-guilherme

# Install dependencies
npm install

# Run development server
npm run dev
```

### Testing
```bash
# Run Playwright tests (if available)
npm test

# Run E2E tests
npx playwright test
```

### Code Quality
```bash
# Lint code (if configured)
npx eslint .

# Format code (if configured)
npx prettier --write .
```

### Documentation
```bash
# Generate documentation
npm run docs
```

## License

This script is provided under the MIT License. Feel free to modify and distribute according to your project's requirements.

## Support

For issues and support, please:
1. Check the GitHub repository for existing issues
2. Open a new issue with detailed error information
3. Provide steps to reproduce the problem
4. Include relevant log files for debugging

## Acknowledgments

- Inspired by best practices in Node.js development
- Based on Docker Compose service orchestration patterns
- Follows shell script development conventions
- Includes comprehensive error handling and logging