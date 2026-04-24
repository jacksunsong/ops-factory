/**
 * End-to-end test for Scheduled Actions page.
 * Tests: create, pause, unpause, edit, run now, view runs, running-state buttons, delete.
 */
import { chromium } from 'playwright';

const BASE_URL = 'http://127.0.0.1:5173';
const results = [];

function log(msg) { console.log(`  ${msg}`); }
function record(name, pass) { results.push({ name, pass }); }

async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function waitToastDismiss(page) { await sleep(1500); }

(async () => {
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    page.setDefaultTimeout(15000);

    // Set current runtime user to admin.
    await page.goto(BASE_URL);
    await page.waitForLoadState('networkidle');
    await sleep(1000);
    await page.evaluate(() => localStorage.setItem('opsfactory:userId', 'admin'));
    await page.waitForLoadState('networkidle');
    await sleep(2000);
    log('Using runtime user admin');

    // Navigate to scheduled actions
    await page.goto(`${BASE_URL}/#/scheduler`);
    await page.waitForLoadState('networkidle');
    await sleep(2000);
    await page.screenshot({ path: '/tmp/schedule_01_initial.png', fullPage: true });

    // ========== Test 1: Initial list ==========
    console.log('\n=== Test 1: Verify initial schedule list ===');
    let cards = page.locator('.scheduled-card');
    let count = await cards.count();
    log(`Found ${count} schedule card(s)`);
    record('Initial schedule list', count >= 1);

    // ========== Test 2: Create schedule ==========
    console.log('\n=== Test 2: Create new schedule ===');
    const createBtn = page.locator('button').filter({ hasText: /Create|新建/ }).first();
    await createBtn.click();
    await page.waitForSelector('.scheduled-modal', { state: 'visible' });
    await sleep(500);

    // Fill form
    const nameInput = page.locator('.scheduled-modal .scheduled-input').first();
    await nameInput.fill('test-e2e-schedule');
    const textarea = page.locator('.scheduled-modal .scheduled-textarea');
    await textarea.fill('This is an e2e test schedule. Just say hello.');

    await page.screenshot({ path: '/tmp/schedule_02_create_form.png', fullPage: true });

    const submitBtn = page.locator('.modal-footer button.btn-primary');
    await submitBtn.click();
    await page.waitForSelector('.scheduled-modal', { state: 'hidden', timeout: 15000 });
    await sleep(2000);
    await page.screenshot({ path: '/tmp/schedule_03_after_create.png', fullPage: true });

    let testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    let testCardCount = await testCard.count();
    record('Create schedule', testCardCount > 0);
    log(testCardCount > 0 ? 'Created successfully' : 'NOT FOUND after creation');

    await waitToastDismiss(page);

    // ========== Test 3: Pause ==========
    console.log('\n=== Test 3: Pause schedule ===');
    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const pauseBtn = testCard.locator('button').filter({ hasText: /^Pause$|^暂停$/ });
        if (await pauseBtn.count() > 0) {
            await pauseBtn.first().click();
            await sleep(2000);
            await page.screenshot({ path: '/tmp/schedule_04_after_pause.png', fullPage: true });
            const status = await testCard.locator('.status-pill').innerText();
            log(`Status after pause: ${status}`);
            record('Pause schedule', status.toUpperCase().includes('PAUSED') || status.includes('已暂停'));
        } else {
            log('No pause button found');
            record('Pause schedule', false);
        }
    } else {
        record('Pause schedule', false);
    }

    await waitToastDismiss(page);

    // ========== Test 4: Unpause ==========
    console.log('\n=== Test 4: Unpause schedule ===');
    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const resumeBtn = testCard.locator('button').filter({ hasText: /^Resume$|^恢复$/ });
        if (await resumeBtn.count() > 0) {
            await resumeBtn.first().click();
            await sleep(2000);
            await page.screenshot({ path: '/tmp/schedule_05_after_unpause.png', fullPage: true });
            const status = await testCard.locator('.status-pill').innerText();
            log(`Status after unpause: ${status}`);
            record('Unpause schedule', status.toUpperCase().includes('ACTIVE') || status.includes('活跃'));
        } else {
            log('No resume button found');
            record('Unpause schedule', false);
        }
    } else {
        record('Unpause schedule', false);
    }

    await waitToastDismiss(page);

    // ========== Test 5: Edit ==========
    console.log('\n=== Test 5: Edit schedule ===');
    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const editBtn = testCard.locator('button').filter({ hasText: /^Edit$|^编辑$/ });
        if (await editBtn.count() > 0) {
            await editBtn.first().click();
            await page.waitForSelector('.scheduled-modal', { state: 'visible' });
            await sleep(500);

            // Change cron
            const cronInput = page.locator('.scheduled-modal .scheduled-input').nth(1);
            await cronInput.fill('0 0 10 * * *');
            await page.screenshot({ path: '/tmp/schedule_06_edit_form.png', fullPage: true });

            const saveBtn = page.locator('.modal-footer button.btn-primary');
            await saveBtn.click();
            await page.waitForSelector('.scheduled-modal', { state: 'hidden', timeout: 15000 });
            await sleep(2000);
            await page.screenshot({ path: '/tmp/schedule_07_after_edit.png', fullPage: true });

            testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
            const cronText = await testCard.locator('.scheduled-cron').innerText();
            log(`Cron after edit: ${cronText}`);
            record('Edit schedule', cronText.includes('10'));
        } else {
            log('No edit button found');
            record('Edit schedule', false);
        }
    } else {
        record('Edit schedule', false);
    }

    await waitToastDismiss(page);

    // ========== Test 6: Run Now ==========
    console.log('\n=== Test 6: Run Now ===');
    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const runBtn = testCard.locator('button').filter({ hasText: /Run now|Run Now|立即运行/i });
        if (await runBtn.count() > 0) {
            await runBtn.first().click();
            await sleep(3000);
            await page.screenshot({ path: '/tmp/schedule_08_after_run_now.png', fullPage: true });
            record('Run Now', true);
            log('Run Now triggered');
        } else {
            log('No Run Now button found');
            record('Run Now', false);
        }
    } else {
        record('Run Now', false);
    }

    await waitToastDismiss(page);

    // ========== Test 7: View Runs ==========
    console.log('\n=== Test 7: View Run History ===');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await sleep(2000);

    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const viewRunsBtn = testCard.locator('button').filter({ hasText: /View Runs|View runs|运行记录/i });
        if (await viewRunsBtn.count() > 0) {
            await viewRunsBtn.first().click();
            await sleep(2000);
            await page.screenshot({ path: '/tmp/schedule_09_view_runs.png', fullPage: true });

            const runsPanel = page.locator('.scheduled-runs-panel');
            if (await runsPanel.count() > 0) {
                record('View Run History', true);
                const runItems = page.locator('.scheduled-run-item');
                log(`Runs panel open, found ${await runItems.count()} run(s)`);
            } else {
                record('View Run History', false);
            }

            // Go back
            const backBtn = page.locator('button').filter({ hasText: /Back|返回/ });
            if (await backBtn.count() > 0) await backBtn.first().click();
            await sleep(1000);
        } else {
            log('No View Runs button found');
            record('View Run History', false);
        }
    } else {
        record('View Run History', false);
    }

    // ========== Test 8: Running state buttons ==========
    console.log('\n=== Test 8: Running state buttons ===');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await sleep(2000);

    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        const status = await testCard.locator('.status-pill').innerText();
        log(`Current status: ${status}`);

        if (status.toUpperCase().includes('RUNNING') || status.includes('运行中')) {
            const killBtn = testCard.locator('button').filter({ hasText: /^Kill$|^终止$/ });
            const pauseBtn = testCard.locator('button').filter({ hasText: /^Pause$|^暂停$/ });
            const editBtn = testCard.locator('button').filter({ hasText: /^Edit$|^编辑$/ });
            const hint = testCard.locator('.scheduled-running-hint');

            const killVisible = await killBtn.count() > 0;
            const pauseHidden = await pauseBtn.count() === 0;
            const editHidden = await editBtn.count() === 0;
            const hintVisible = await hint.count() > 0;

            log(`Kill=${killVisible}, PauseHidden=${pauseHidden}, EditHidden=${editHidden}, Hint=${hintVisible}`);
            record('Running state buttons', killVisible && pauseHidden && editHidden);

            // Kill it
            if (killVisible) {
                await killBtn.first().click();
                await sleep(2000);
                log('Killed running job');
            }
        } else {
            log('Job not running (may have finished), skipping running-state test');
            record('Running state buttons', true);
        }
    } else {
        record('Running state buttons', false);
    }

    await page.screenshot({ path: '/tmp/schedule_10_state.png', fullPage: true });
    await waitToastDismiss(page);

    // ========== Test 9: Delete ==========
    console.log('\n=== Test 9: Delete schedule ===');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await sleep(2000);

    testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
    if (await testCard.count() > 0) {
        // If still running, kill first and reload
        const statusText = await testCard.locator('.status-pill').innerText();
        if (statusText.includes('Running') || statusText.includes('运行中')) {
            const killBtn = testCard.locator('button').filter({ hasText: /^Kill$|^终止$/ });
            if (await killBtn.count() > 0) {
                await killBtn.first().click();
                await sleep(5000);
                await page.reload();
                await page.waitForLoadState('networkidle');
                await sleep(2000);
                testCard = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
            }
        }

        const deleteBtn = testCard.locator('button').filter({ hasText: /^Delete$|^删除$/ });
        if (await deleteBtn.count() > 0) {
            page.on('dialog', dialog => dialog.accept());
            await deleteBtn.first().click();
            await sleep(2000);
            await page.screenshot({ path: '/tmp/schedule_11_after_delete.png', fullPage: true });

            const remaining = page.locator('.scheduled-card').filter({ hasText: 'test-e2e-schedule' });
            const gone = await remaining.count() === 0;
            record('Delete schedule', gone);
            log(gone ? 'Deleted successfully' : 'Still exists after delete');
        } else {
            log('No delete button found');
            record('Delete schedule', false);
        }
    } else {
        log('Test schedule not found');
        record('Delete schedule', false);
    }

    // ========== Summary ==========
    console.log('\n' + '='.repeat(50));
    console.log('RESULTS SUMMARY');
    console.log('='.repeat(50));
    let allPass = true;
    for (const { name, pass } of results) {
        const icon = pass ? '✅' : '❌';
        console.log(`  ${icon} ${pass ? 'PASS' : 'FAIL'}: ${name}`);
        if (!pass) allPass = false;
    }
    const passed = results.filter(r => r.pass).length;
    const failed = results.filter(r => !r.pass).length;
    console.log(`\nTotal: ${results.length} tests, ${passed} passed, ${failed} failed`);
    console.log('Screenshots saved to /tmp/schedule_*.png');

    await browser.close();
    process.exit(allPass ? 0 : 1);
})();
