const dashboard = document.getElementById('dashboard');
const campo = (nome) => document.getElementById(`dashboard-${nome}`);
const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const numero = new Intl.NumberFormat('pt-BR');
const horario = new Intl.DateTimeFormat('pt-BR', { timeStyle: 'short', timeZone: 'America/Sao_Paulo' });
const statusPedidos = {
    RECEBIDO: 'Recebido',
    EM_PREPARACAO: 'Em preparação',
    PRONTO: 'Pronto',
    SAIU_PARA_ENTREGA: 'Saiu para entrega',
    RETIRADO: 'Retirado',
    FINALIZADO: 'Finalizado',
    CANCELADO: 'Cancelado',
    OUTROS: 'Outros'
};
const graficos = {};

function mostrarMensagem(texto, tipo) {
    campo('mensagem').textContent = texto;
    campo('mensagem').className = `alert alert-${tipo}`;
    campo('mensagem').hidden = texto === '';
}

async function consultar(rota, filtros) {
    const resposta = await fetch(`${rota}?${filtros}`);
    if (resposta.status === 401) {
        window.location.assign(dashboard.dataset.login);
        throw new Error('Sua sessão expirou. Entre novamente.');
    }
    if (resposta.status === 403) {
        throw new Error('Seu perfil não tem acesso ao dashboard.');
    }
    if (resposta.status === 400) {
        const erro = await resposta.json();
        throw new Error(erro.detail);
    }
    if (!resposta.ok) {
        throw new Error('Não foi possível consultar os pedidos. Clique em Atualizar para tentar novamente.');
    }
    return resposta.json();
}

function desenharGrafico(id, tipo, rotulos, valores, cor, formatarValor) {
    graficos[id]?.destroy();
    graficos[id] = new Chart(campo(id), {
        type: tipo,
        data: {
            labels: rotulos,
            datasets: [{ data: valores, borderColor: cor, backgroundColor: cor, fill: false }]
        },
        options: {
            maintainAspectRatio: false,
            legend: { display: false },
            tooltips: { callbacks: { label: (item) => formatarValor(item.yLabel) } },
            scales: { yAxes: [{ ticks: { beginAtZero: true, precision: 0, callback: formatarValor } }] }
        }
    });
}

function renderizarResumo(resumo) {
    campo('faturamento').textContent = moeda.format(resumo.faturamento);
    campo('total').textContent = numero.format(resumo.totalPedidos);
    campo('finalizados').textContent = numero.format(resumo.pedidosFinalizados);
    campo('ticket').textContent = moeda.format(resumo.ticketMedio);

    const rotulos = resumo.serieDiaria.map((dia) => dia.data.slice(5).split('-').reverse().join('/'));
    desenharGrafico('grafico-vendas', 'line', rotulos,
        resumo.serieDiaria.map((dia) => dia.faturamento), '#0d6efd', moeda.format);
    desenharGrafico('grafico-pedidos', 'bar', rotulos,
        resumo.serieDiaria.map((dia) => dia.pedidos), '#198754', numero.format);

    campo('contagem-status').replaceChildren();
    for (const [status, quantidade] of Object.entries(resumo.porStatus)) {
        const item = document.createElement('li');
        item.className = 'list-group-item px-0';
        item.textContent = `${statusPedidos[status]} · ${numero.format(quantidade)}`;
        campo('contagem-status').append(item);
    }

    campo('ranking').replaceChildren();
    for (const produto of resumo.ranking) {
        const item = document.createElement('li');
        item.textContent = `${produto.nome} · ${numero.format(produto.quantidade)} un.`;
        campo('ranking').append(item);
    }
    campo('sem-ranking').hidden = resumo.ranking.length > 0;
}

function renderizarPedidos(pedidos) {
    const tabela = campo('pedidos');
    tabela.replaceChildren();
    for (const pedido of pedidos) {
        const [data, hora] = pedido.entrada.split('T');
        const entrada = `${data.split('-').reverse().join('/')} ${hora.slice(0, 5)}`;
        const total = pedido.total == null ? '—' : moeda.format(pedido.total);
        const linha = tabela.insertRow();
        for (const valor of [pedido.id, entrada, statusPedidos[pedido.status], total]) {
            linha.insertCell().textContent = valor;
        }
    }
    if (pedidos.length === 0) {
        const celula = tabela.insertRow().insertCell();
        celula.colSpan = 4;
        celula.textContent = 'Nenhum pedido encontrado para este filtro.';
    }
}

async function atualizarDashboard() {
    const formulario = campo('filtros');
    const filtros = new URLSearchParams();
    const inicio = campo('inicio').value;
    const fim = campo('fim').value;
    if (inicio || fim) {
        filtros.set('inicio', inicio);
        filtros.set('fim', fim);
    }

    const status = campo('status').value;
    for (const elemento of formulario.elements) elemento.disabled = true;
    campo('resultados').hidden = true;
    mostrarMensagem('Carregando dados…', 'info');
    try {
        const resumo = await consultar(dashboard.dataset.resumo, filtros);
        campo('inicio').value = resumo.inicio;
        campo('fim').value = resumo.fim;
        filtros.set('inicio', resumo.inicio);
        filtros.set('fim', resumo.fim);
        if (status) filtros.set('status', status);
        const pedidos = await consultar(dashboard.dataset.pedidos, filtros);

        campo('resultados').hidden = false;
        renderizarResumo(resumo);
        renderizarPedidos(pedidos);
        mostrarMensagem(resumo.totalPedidos === 0 ? 'Nenhum pedido no período selecionado.' : '', 'info');
        campo('atualizado').textContent = `Última atualização às ${horario.format(new Date())} (Brasília)`;
    } catch (erro) {
        campo('resultados').hidden = true;
        mostrarMensagem(erro instanceof TypeError ? 'Falha na consulta. Clique em Atualizar para tentar novamente.'
            : erro.message, 'danger');
    } finally {
        for (const elemento of formulario.elements) elemento.disabled = false;
    }
}

for (const [status, nome] of Object.entries(statusPedidos)) {
    campo('status').add(new Option(nome, status));
}
campo('filtros').addEventListener('submit', (evento) => {
    evento.preventDefault();
    atualizarDashboard();
});
atualizarDashboard();
