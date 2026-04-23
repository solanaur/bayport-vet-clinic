#!/usr/bin/env node

/**
 * Verification script to check if desktop app setup is correct
 * Run: node VERIFY_SETUP.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

console.log('========================================');
console.log('Bayport Desktop App - Setup Verification');
console.log('========================================\n');

let errors = [];
let warnings = [];

// Check Node.js version
console.log('[1/8] Checking Node.js version...');
try {
  const nodeVersion = execSync('node --version', { encoding: 'utf-8' }).trim();
  const majorVersion = parseInt(nodeVersion.replace('v', '').split('.')[0]);
  if (majorVersion >= 18) {
    console.log(`  ✓ Node.js ${nodeVersion} (OK)`);
  } else {
    errors.push(`Node.js version ${nodeVersion} is too old. Need v18.0.0 or higher.`);
    console.log(`  ✗ Node.js ${nodeVersion} (TOO OLD - Need v18+)`);
  }
} catch (err) {
  errors.push('Node.js is not installed or not in PATH');
  console.log('  ✗ Node.js not found');
}

// Check npm
console.log('\n[2/8] Checking npm...');
try {
  const npmVersion = execSync('npm --version', { encoding: 'utf-8' }).trim();
  console.log(`  ✓ npm ${npmVersion} (OK)`);
} catch (err) {
  errors.push('npm is not installed or not in PATH');
  console.log('  ✗ npm not found');
}

// Check Java
console.log('\n[3/8] Checking Java...');
try {
  const javaVersion = execSync('java -version', { encoding: 'utf-8', stdio: 'pipe' });
  const versionMatch = javaVersion.match(/version "(\d+)/);
  if (versionMatch) {
    const majorVersion = parseInt(versionMatch[1]);
    if (majorVersion >= 17) {
      console.log(`  ✓ Java ${majorVersion} (OK)`);
    } else {
      errors.push(`Java version ${majorVersion} is too old. Need Java 17 or higher.`);
      console.log(`  ✗ Java ${majorVersion} (TOO OLD - Need Java 17+)`);
    }
  } else {
    warnings.push('Could not determine Java version');
    console.log('  ⚠ Java found but version unclear');
  }
} catch (err) {
  errors.push('Java is not installed or not in PATH');
  console.log('  ✗ Java not found');
}

// Check if node_modules exists
console.log('\n[4/8] Checking Node.js dependencies...');
const nodeModulesPath = path.join(__dirname, 'node_modules');
if (fs.existsSync(nodeModulesPath)) {
  console.log('  ✓ node_modules directory exists');
} else {
  warnings.push('node_modules not found. Run: npm install');
  console.log('  ⚠ node_modules not found (run: npm install)');
}

// Check if backend JAR exists
console.log('\n[5/8] Checking backend JAR...');
const backendJarPath = path.join(__dirname, '..', 'bayport-backend', 'target', 'bayport-backend-0.0.1-SNAPSHOT.jar');
if (fs.existsSync(backendJarPath)) {
  const stats = fs.statSync(backendJarPath);
  const sizeMB = (stats.size / (1024 * 1024)).toFixed(2);
  console.log(`  ✓ Backend JAR found (${sizeMB} MB)`);
} else {
  warnings.push('Backend JAR not found. Run: npm run build-backend');
  console.log('  ⚠ Backend JAR not found (run: npm run build-backend)');
}

// Check if frontend files exist
console.log('\n[6/8] Checking frontend files...');
const frontendFiles = ['index.html', 'dashboard.html', 'pet-records.html', 'appointments.html'];
let frontendOk = true;
for (const file of frontendFiles) {
  const filePath = path.join(__dirname, '..', file);
  if (!fs.existsSync(filePath)) {
    errors.push(`Frontend file missing: ${file}`);
    frontendOk = false;
  }
}
if (frontendOk) {
  console.log('  ✓ Frontend files found');
} else {
  console.log('  ✗ Some frontend files are missing');
}

// Check if assets exist
console.log('\n[7/8] Checking assets...');
const assetsPath = path.join(__dirname, '..', 'assets');
if (fs.existsSync(assetsPath)) {
  console.log('  ✓ Assets directory exists');
} else {
  warnings.push('Assets directory not found');
  console.log('  ⚠ Assets directory not found');
}

// Check port 8080 availability (basic check)
console.log('\n[8/8] Checking port 8080...');
try {
  const netstat = process.platform === 'win32' 
    ? execSync('netstat -ano | findstr :8080', { encoding: 'utf-8', stdio: 'pipe' })
    : execSync('lsof -ti:8080', { encoding: 'utf-8', stdio: 'pipe' });
  warnings.push('Port 8080 appears to be in use. The app may not start if another service is using it.');
  console.log('  ⚠ Port 8080 may be in use');
} catch (err) {
  // Port is free (command failed = no process found)
  console.log('  ✓ Port 8080 is available');
}

// Summary
console.log('\n========================================');
console.log('Verification Summary');
console.log('========================================\n');

if (errors.length === 0 && warnings.length === 0) {
  console.log('✓ All checks passed! You\'re ready to run the desktop app.');
  console.log('\nNext steps:');
  console.log('  npm run desktop    # Build and start the app');
  console.log('  npm run package    # Create installer');
  process.exit(0);
} else {
  if (errors.length > 0) {
    console.log('✗ ERRORS (must fix):');
    errors.forEach(err => console.log(`  - ${err}`));
  }
  if (warnings.length > 0) {
    console.log('\n⚠ WARNINGS (should fix):');
    warnings.forEach(warn => console.log(`  - ${warn}`));
  }
  console.log('\nPlease fix the issues above before running the app.');
  process.exit(errors.length > 0 ? 1 : 0);
}

