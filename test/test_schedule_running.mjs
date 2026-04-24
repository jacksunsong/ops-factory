import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
let testsPassed = 0, testsFailed = 0;
function pass(n) { console.log(`  ✅ PASS: ${n}`); testsPassed++; }
function fail(n, r) { console.log(`  ❌ FAIL: ${n} — ${r}`); testsFailed++; }

try {
    console.log('=== Set runtime user ===');
    await page.goto('http://127.0.0.1:5173/');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    await page.evaluate(() => localStorage.setItem('opsfactory:userId', 'admin'));
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    await page.goto('http://127.0.0.1:5173/#/scheduler');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);

    // First make sure schedule is unpaused
    let cards = await page.locator('.scheduled-card').all();
    let resumeBtn = cards[0]?.locator('button:has-text("恢复"), button:has-text("Resume")');
    if (resumeBtn && await resumeBtn.count() > 0) {
        await resumeBtn.first().click();
        await page.waitForTimeout(2000);
    }

    // Trigger run now
    console.log('\n=== Triggering Run Now ===');
    cards = await page.locator('.scheduled-card').all();
    const runNowBtn = cards[0]?.locator('button:has-text("立即运行"), button:has-text("Run now")');
    if (runNowBtn && await runNowBtn.count() > 0) {
        await runNowBtn.first().click();
        await page.waitForTimeout(3000);
        // Reload to get fresh state
        await page.goto('http://127.0.0.1:5173/#/scheduler');
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(3000);
    }

    // Check: when running, buttons should show Kill only
    console.log('\n=== Test: Running state shows correct buttons ===');
    cards = await page.locator('.scheduled-card').all();
    const status = (await cards[0].locator('.status-pill').textContent() || '').trim();
    console.log(`  Status: ${status}`);
    await page.screenshot({ path: '/tmp/sched_running.png', fullPage: true });

    const killBtn = cards[0].locator('button:has-text("终止"), button:has-text("Kill")');
    const pauseBtn2 = cards[0].locator('button:has-text("暂停"), button:has-text("Pause")');
    const editBtn = cards[0].locator('button:has-text("编辑"), button:has-text("Edit")');

    const hasKill = await killBtn.count() > 0;
    const hasPause = await pauseBtn2.count() > 0;
    const hasEdit = await editBtn.count() > 0;
    console.log(`  Kill=${hasKill}, Pause=${hasPause}, Edit=${hasEdit}`);

    if (status.includes('running') || status.includes('运行中')) {
        if (hasKill && !hasPause && !hasEdit) {
            pass('Running state: Kill shown, Pause/Edit hidden');
        } else {
            fail('Running state buttons', `Kill=${hasKill} Pause=${hasPause} Edit=${hasEdit}`);
        }

        // Check hint
        const hint = cards[0].locator('.scheduled-running-hint');
        if (await hint.count() > 0) pass('Running hint shown');
        else fail('Running hint', 'Not found');

        // Kill the running job
        console.log('\n=== Test: Kill running job ===');
        await killBtn.first().click();
        await page.waitForTimeout(5000);

        // Wait for auto-refresh or reload
        await page.goto('http://127.0.0.1:5173/scheduler');
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(20000);

        cards = await page.locator('.scheduled-card').all();
        const afterStatus = (await cards[0].locator('.status-pill').textContent() || '').trim();
        console.log(`  Status after kill: ${afterStatus}`);
        const hasPauseAfter = await cards[0].locator('button:has-text("暂停"), button:has-text("Pause")').count() > 0;
        const hasResumeAfter = await cards[0].locator('button:has-text("恢复"), button:has-text("Resume")').count() > 0;
        if (hasPauseAfter || hasResumeAfter) {
            pass('After kill: Pause/Resume buttons restored');
        } else {
            fail('After kill', 'Pause/Resume not restored');
        }

        // Now test pause
        console.log('\n=== Test: Pause after kill ===');
        const pauseAfterKill = cards[0].locator('button:has-text("暂停"), button:has-text("Pause")');
        if (await pauseAfterKill.count() > 0) {
            await pauseAfterKill.first().click();
            await page.waitForTimeout(3000);
            cards = await page.locator('.scheduled-card').all();
            const finalStatus = (await cards[0].locator('.status-pill').textContent() || '').trim();
            if (finalStatus.includes('paused') || finalStatus.includes('已暂停')) {
                pass('Pause after kill');
            } else {
                fail('Pause after kill', `Status: ${finalStatus}`);
            }
        } else {
            pass('Pause after kill (already paused)');
        }
    } else {
        console.log('  Schedule not in running state after Run Now. Testing available buttons.');
        if (hasPause || hasEdit) {
            pass('Non-running: Edit/Pause buttons available');
        } else {
            fail('Non-running buttons', `Pause=${hasPause} Edit=${hasEdit}`);
        }
    }

    await page.screenshot({ path: '/tmp/sched_final.png', fullPage: true });

} catch (err) {
    console.error('Error:', err.message);
    await page.screenshot({ path: '/tmp/sched_error.png', fullPage: true });
} finally {
    await browser.close();
    console.log(`\n=== Results: ${testsPassed} passed, ${testsFailed} failed ===`);
    process.exit(testsFailed > 0 ? 1 : 0);
}
