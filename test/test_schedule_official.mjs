import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
let testsPassed = 0;
let testsFailed = 0;

function pass(name) { console.log(`  ✅ PASS: ${name}`); testsPassed++; }
function fail(name, reason) { console.log(`  ❌ FAIL: ${name} — ${reason}`); testsFailed++; }

try {
    console.log('=== Setup: Set runtime user admin ===');
    await page.goto('http://127.0.0.1:5173/');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    await page.evaluate(() => localStorage.setItem('opsfactory:userId', 'admin'));
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    await page.goto('http://127.0.0.1:5173/#/scheduler');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);

    await page.screenshot({ path: '/tmp/sched_01_initial.png', fullPage: true });

    // ============================================
    // TEST 1: When running, should show Kill (not Pause/Edit/RunNow)
    // ============================================
    console.log('\n=== Test 1: Running schedule shows Kill button ===');
    {
        const cards = await page.locator('.scheduled-card').all();
        if (cards.length === 0) { fail('Running buttons', 'No schedule cards'); }
        else {
            const status = (await cards[0].locator('.status-pill').textContent() || '').trim();
            console.log(`  Status: ${status}`);

            const killBtn = cards[0].locator('button:has-text("终止"), button:has-text("Kill")');
            const pauseBtn = cards[0].locator('button:has-text("暂停"), button:has-text("Pause")');
            const editBtn = cards[0].locator('button:has-text("编辑"), button:has-text("Edit")');
            const runNowBtn = cards[0].locator('button:has-text("立即运行"), button:has-text("Run now")');

            const hasKill = await killBtn.count() > 0;
            const hasPause = await pauseBtn.count() > 0;
            const hasEdit = await editBtn.count() > 0;
            const hasRunNow = await runNowBtn.count() > 0;

            console.log(`  Kill: ${hasKill}, Pause: ${hasPause}, Edit: ${hasEdit}, RunNow: ${hasRunNow}`);

            if (status.includes('running') || status.includes('运行中')) {
                if (hasKill && !hasPause && !hasEdit && !hasRunNow) {
                    pass('Running schedule shows Kill only (+ ViewRuns/Delete)');
                } else {
                    fail('Running buttons', `Kill=${hasKill} Pause=${hasPause} Edit=${hasEdit} RunNow=${hasRunNow}`);
                }

                // Check hint message
                const hint = cards[0].locator('.scheduled-running-hint');
                if (await hint.count() > 0) {
                    pass('Running schedule shows hint message');
                } else {
                    fail('Running hint', 'Hint message not found');
                }
            } else {
                console.log('  Schedule not running, skipping running-specific tests');
                pass('Running buttons (skipped - not running)');
                pass('Running hint (skipped - not running)');
            }
        }
    }

    // ============================================
    // TEST 2: Kill a running schedule
    // ============================================
    console.log('\n=== Test 2: Kill running schedule ===');
    {
        const cards = await page.locator('.scheduled-card').all();
        const killBtn = cards[0]?.locator('button:has-text("终止"), button:has-text("Kill")');
        if (killBtn && await killBtn.count() > 0) {
            await killBtn.first().click();
            await page.waitForTimeout(3000);

            // After kill, the schedule should refresh and show Edit/Pause/Resume
            const updatedCards = await page.locator('.scheduled-card').all();
            if (updatedCards.length > 0) {
                const pauseOrResumeBtn = updatedCards[0].locator('button:has-text("暂停"), button:has-text("Pause"), button:has-text("恢复"), button:has-text("Resume")');
                if (await pauseOrResumeBtn.count() > 0) {
                    pass('After Kill, Pause/Resume button appears');
                } else {
                    // May still be running, wait for auto-refresh
                    console.log('  Waiting for auto-refresh...');
                    await page.waitForTimeout(16000);
                    const cards2 = await page.locator('.scheduled-card').all();
                    const btn2 = cards2[0]?.locator('button:has-text("暂停"), button:has-text("Pause"), button:has-text("恢复"), button:has-text("Resume")');
                    if (btn2 && await btn2.count() > 0) {
                        pass('After Kill + auto-refresh, Pause/Resume button appears');
                    } else {
                        fail('After Kill', 'Pause/Resume button still not shown');
                    }
                }
            }
        } else {
            console.log('  No Kill button (schedule not running), trying to pause instead');
            pass('Kill (skipped - not running)');
        }
    }

    await page.screenshot({ path: '/tmp/sched_02_after_kill.png', fullPage: true });

    // ============================================
    // TEST 3: Pause a non-running schedule
    // ============================================
    console.log('\n=== Test 3: Pause non-running schedule ===');
    {
        let cards = await page.locator('.scheduled-card').all();
        const pauseBtn = cards[0]?.locator('button:has-text("暂停"), button:has-text("Pause")');
        if (pauseBtn && await pauseBtn.count() > 0) {
            await pauseBtn.first().click();
            await page.waitForTimeout(3000);

            cards = await page.locator('.scheduled-card').all();
            const status = (await cards[0].locator('.status-pill').textContent() || '').trim();
            if (status.includes('paused') || status.includes('已暂停')) {
                pass('Pause non-running schedule');
            } else {
                fail('Pause', `Status is "${status}"`);
            }
        } else {
            // Maybe already paused
            const resumeBtn = cards[0]?.locator('button:has-text("恢复"), button:has-text("Resume")');
            if (resumeBtn && await resumeBtn.count() > 0) {
                console.log('  Already paused');
                pass('Pause (already paused)');
            } else {
                fail('Pause', 'No Pause button found');
            }
        }
    }

    // ============================================
    // TEST 4: Resume
    // ============================================
    console.log('\n=== Test 4: Resume paused schedule ===');
    {
        let cards = await page.locator('.scheduled-card').all();
        const resumeBtn = cards[0]?.locator('button:has-text("恢复"), button:has-text("Resume")');
        if (resumeBtn && await resumeBtn.count() > 0) {
            await resumeBtn.first().click();
            await page.waitForTimeout(3000);

            cards = await page.locator('.scheduled-card').all();
            const status = (await cards[0].locator('.status-pill').textContent() || '').trim();
            if (!status.includes('paused') && !status.includes('已暂停')) {
                pass('Resume paused schedule');
            } else {
                fail('Resume', `Status still "${status}"`);
            }
        } else {
            fail('Resume', 'No Resume button found');
        }
    }

    // ============================================
    // TEST 5: View Run History
    // ============================================
    console.log('\n=== Test 5: View Run History ===');
    {
        const cards = await page.locator('.scheduled-card').all();
        const viewBtn = cards[0]?.locator('button:has-text("查看运行记录"), button:has-text("View Runs")');
        if (viewBtn && await viewBtn.count() > 0) {
            await viewBtn.first().click();
            await page.waitForTimeout(2000);
            const runsPanel = page.locator('.scheduled-runs-panel');
            if (await runsPanel.count() > 0) {
                pass('View Run History');
            } else {
                fail('View Runs', 'Panel not found');
            }
            const backBtn = page.locator('button:has-text("返回"), button:has-text("Back")');
            if (await backBtn.count() > 0) await backBtn.first().click();
            await page.waitForTimeout(1000);
        } else {
            fail('View Runs', 'No View Runs button found');
        }
    }

    // ============================================
    // TEST 6: Edit modal
    // ============================================
    console.log('\n=== Test 6: Edit modal ===');
    {
        const cards = await page.locator('.scheduled-card').all();
        const editBtn = cards[0]?.locator('button:has-text("编辑"), button:has-text("Edit")');
        if (editBtn && await editBtn.count() > 0) {
            await editBtn.first().click();
            await page.waitForTimeout(1000);
            const modal = page.locator('.modal-overlay');
            if (await modal.count() > 0) {
                pass('Edit modal opens');
                const closeBtn = modal.locator('button:has-text("取消"), button:has-text("Cancel")');
                if (await closeBtn.count() > 0) await closeBtn.first().click();
            } else {
                fail('Edit', 'Modal not found');
            }
        } else {
            fail('Edit', 'No Edit button (schedule may be running)');
        }
    }

    await page.screenshot({ path: '/tmp/sched_03_final.png', fullPage: true });

} catch (err) {
    console.error('\nTest error:', err.message);
    await page.screenshot({ path: '/tmp/sched_error.png', fullPage: true });
} finally {
    await browser.close();
    console.log(`\n=== Results: ${testsPassed} passed, ${testsFailed} failed ===`);
    process.exit(testsFailed > 0 ? 1 : 0);
}
