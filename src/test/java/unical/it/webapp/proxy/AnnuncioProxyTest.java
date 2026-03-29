package unical.it.webapp.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unical.it.webapp.dao.FotoDAO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Foto;

@ExtendWith(MockitoExtension.class)
class AnnuncioProxyTest {

    @Mock
    private FotoDAO fotoDAO;

    @Test
    void getFoto_lazyLoadsOnce() {
        Annuncio annuncio = new Annuncio();
        annuncio.setId(7L);
        Foto a = Foto.builder().id(1L).build();
        when(fotoDAO.findByAnnuncioId(7L)).thenReturn(List.of(a));

        AnnuncioProxy proxy = new AnnuncioProxy(annuncio, fotoDAO);

        assertThat(proxy.getFoto()).hasSize(1).containsExactly(a);
        assertThat(proxy.getFoto()).hasSize(1);

        verify(fotoDAO).findByAnnuncioId(7L);
        verifyNoMoreInteractions(fotoDAO);
    }
}
